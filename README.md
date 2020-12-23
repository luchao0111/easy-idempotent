# easy-idempotent
简单幂等组件
# 1  背景
什么是幂等，百度词条定义为：“在编程中一个幂等操作的特点是其任意多次执行所产生的影响均与一次执行的影响相同”。
基本上所有业务系统中的幂等都是各自进行处理，采用统一处理，需要考虑的内容会比较多。核心的业务还是适合业务方自己去处理，比如订单支付，会有支付记录表，一个订单只能被支付一次，通过支付记录表就可以达到幂等的效果。
还有一些不是核心的业务，但是也有幂等的需求。比如网络问题，多次重试、用户点击多次等场景。这种场景下还是需要一个通用的幂等框架来处理，会让业务开发更加简单。
# 2	 简单幂等实现
幂等的实现其实并不复杂，方案也有很多种，诸如：MVCC方案、去重表、悲观锁、状态机、全局ID等。首先介绍下基于数据库记录的方案来实现，后面再介绍通用方案。
## 2.1	数据库记录判断
仍以支付场景举例。业务场景是一个订单只能支付一次，所以我们在支付之前会判断这个订单有没有支付过，如果没有支付过则进行支付，如果支付过了，就返回支付成功。
这种方式需要有一个额外的表来存储做过的动作，才能判断之前有没有做过这件事情。就好比一个人，要处理的事务太多了，为避免遗漏疏忽，会在笔记上记录做过的事，并标记事务处理进度。若后续突然想起某件事，需要确实时，即可翻阅查找。
## 2.2	并发问题解决
通过查询支付记录，判断能否进行支付在业务逻辑上没一点问题，但是在并发场景就会有问题。
如编号为NO1001的订单在很短的时间内发起了两次支付请求，当前两个请求同时查询支付记录，都没有查询到，然后都开始支付的逻辑，最后发现同一个订单支付了两次，这就是并发导致的幂等问题。
为了解决并发问题，简单点的直接用数据库的唯一索引解决，稍微麻烦点的都会用分布式锁来对同一个资源进行加锁。比如我们对订单NO1001进行加锁，如果同时发起了两次支付请求，那么同一时间只能有一个请求可以获取锁，另一个请求获取不到锁可以直接失败，也可以等待前面的请求执行完成。如果等待前面的请求执行完成，接着往下处理，就能查到订单NO1001已经支付过了，直接返回支付成功了。
# 3	通用幂等实现
为了能够让开发更专注于业务功能的开发，简单场景的幂等操作我认为可以进行统一封装来处理，下面介绍一下通用幂等的实现。
## 3.1	设计方案 （图）

### 3.1.1	加锁
为了避免并发问题，我们需要对相关资源加锁。基于架构的Redis连接工具库可实现相对可靠的分布式锁，但不局限于该实现方式，通过定义加解锁门面，开发者可自现实，来支持更多的锁方案。
### 3.1.2	幂等判断
一般我们在程序内部做幂等操作都是先查询，然后根据查询的结果再响应不同操作。同时会对相同的资源进行加锁来避免并发问题。
加锁是通用的，不通用的部分就是判断这个操作之前有没有操作过，所以我们需要有一个通用的存储来记录所有的操作。具体的存储方式亦支持开发者自定义实现。
### 3.1.3	使用简单
提供通用的幂等组件，简单配置即可实现幂等，屏蔽加锁，记录判断等逻辑。
### 3.1.4	支持注解
除了通过代码的方式来进行幂等的控制，同时为了让使用更加简单，还需要提供注解的方式来支持幂等，使用者只需要在对应的业务方法上增加对应的注解，即可实现幂等。
### 3.1.5	多级存储
需要支持多级存储，默认提供的存储为 Redis 实现，优点是性能高，适用于大部分场景。因为很多场景都是为了防止短时间内请求重复导致的问题，通过设置一定的失效时间，让 Key 自动失效。

### 3.1.6	支持业务失败
业务执行过程中，可能由网络环境抖动、机器过载等因素导致执行异常，导致最终执行失败。我们不妨考虑如下情况：
当短暂恢复后再次请求，此时因为存储Key已存在而被判定为重复请求而失败，而实际业务并未执行。因此增加支持抛出指定异常时清理缓存Key，在注解中默认对运行时异常下不存储Key。
### 3.1.7	执行流程（图）

## 3.2	幂等接口
继承基类：cn.carbank.IdempotentCommand，并实现run()和getIdempotentResult()；
## 3.3	幂等注解
注解：cn.carbank.annotation.Idempotent，注解在方法上，需要切面环绕。
## 3.4	自动区分重复请求
代码方式处理幂等，需要传入幂等的 Key，注解方式处理幂等，支持配置 Key，支持 SPEL 表达式。这两种都是需要在使用的时候就确定根据什么属性来作为幂等的唯一性判断。
还有一种幂等的场景是比较常见的，就是防止重复提交或者网络问题超时重试。同样的操作会请求多次，这种场景下可以在操作之前先申请一个唯一的 ID，每次请求的时候带给后端，这样就能标识整个请求的唯一性。
组件默认自动生成唯一标识的功能，简单来说就是根据方法入参 MD5，如果 MD5 值没有变化就认为是同一次请求。如果在使用幂等注解的时候没有指定 SPEL Key, 就会使用自动生成的 Key。
## 3.5	自定义锁
实现接口：cn.carbank.locksupport.LockClient
在Spring环境下，需注入到容器中
## 3.6	自定义存储
实现接口：cn.carbank.repository.IdempotentRecordRepo
在Spring环境下，需注入到容器中
## 快速使用

<html>
<p><dependency></p>
        <p><groupId>org.example</groupId></p>
        <p><artifactId>easy-idempotent-starter</artifactId></p>
        <p><version>2.1.4.RELEASE</version></p>
<p></dependency></p>

</html>
