package com.ibm.cloud.cache.redis;
/**
 * 
 */


import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.cache.RedisCache;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.Metrics;
import io.vavr.CheckedFunction0;
import io.vavr.control.Try;

/**
 * RedisCache implementation that uses a resilient4j circuit breaker to bypass the Redis instance calls if the circuit 
 * is open. This allows us to not be sensitive to ICD Redis instabilities. If the call to ICD Redis fails, Spring Cache 
 * will treat it as a cache miss and continue executing the cached method normally.
 */
public class CircuitBreakerRedisCache extends RedisCache {
    
    private static final Logger logger = Logger.getLogger(CircuitBreakerRedisCache.class.getName());
    private static final Level loggerLevel = Level.FINER;
    
    private RedisCache redisCache;
    private CircuitBreaker circuitBreaker;
    
    /**
     * @param name
     * @param cacheWriter
     * @param cacheConfig
     */
    public CircuitBreakerRedisCache(RedisCache redisCache) {
        super(redisCache.getName(), redisCache.getNativeCache(), redisCache.getCacheConfiguration());
        this.redisCache = redisCache;
        ApplicationContext appCtx = ApplicationContextHolder.getContext();
        if (appCtx != null) {
        	this.circuitBreaker = appCtx.getBean(CircuitBreaker.class);
        }
    }
    
    public CircuitBreakerRedisCache(RedisCache redisCache, CircuitBreaker cb) {
        this(redisCache);
        this.circuitBreaker = cb;
    }
    
    @Override
    public ValueWrapper get(Object key) {
    	this.logCBMetrics();
        CheckedFunction0<ValueWrapper> checkedFunction = this.circuitBreaker.decorateCheckedSupplier(() -> this.redisCache.get(key));
        Try<ValueWrapper> result = Try.of(checkedFunction).recoverWith(CallNotPermittedException.class, e -> this.recover(e));
        return result.get();
    }

	protected void logCBMetrics() {
		logCBMetrics(this.circuitBreaker, loggerLevel);
	}
    
    private <T> Try<T> recover(CallNotPermittedException e) {
    	logger.log(loggerLevel, "Circuit broken, cache bypassed: " + e.getMessage());
    	return Try.success(null);
    }
    
    private Void recoverVoid(CallNotPermittedException e) {
    	logger.log(loggerLevel, "Circuit broken, cache bypassed: " + e.getMessage());
    	return null;
    }
    
    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
    	this.logCBMetrics();
        CheckedFunction0<T> checkedFunction = this.circuitBreaker.decorateCheckedSupplier(() -> this.redisCache.get(key, valueLoader));
        Try<T> result = Try.of(checkedFunction).recoverWith(CallNotPermittedException.class, e -> this.recover(e));
        if (result.isFailure()) {
            Throwable cause = result.getCause();
            logger.log(loggerLevel, "Failure from RedisCache.get(): " + cause.getMessage());
        }
        return result.get();
    }
    
    @Override
    public <T> T get(Object key, Class<T> type) {
    	this.logCBMetrics();
        CheckedFunction0<T> checkedFunction = this.circuitBreaker.decorateCheckedSupplier(() -> this.redisCache.get(key, type));
        Try<T> result = Try.of(checkedFunction).recoverWith(CallNotPermittedException.class, e -> this.recover(e));
        if (result.isFailure()) {
            Throwable cause = result.getCause();
            logger.log(loggerLevel, "Failure from RedisCache.get(): " + cause.getMessage());
        }
        return result.get();
    }
    
    @Override
    public void put(Object key, Object value) {
    	this.logCBMetrics();
        Runnable decorateRunnable = this.circuitBreaker.decorateRunnable(() -> this.redisCache.put(key, value));
        Try<Void> result = Try.runRunnable(decorateRunnable).recover(CallNotPermittedException.class, e -> this.recoverVoid(e));
        if (result.isFailure()) {
            Throwable cause = result.getCause();
            logger.log(loggerLevel, "Failure from RedisCache.put(): " + cause.getMessage());
        }
        result.get();
    }
    
    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
    	this.logCBMetrics();
        CheckedFunction0<ValueWrapper> checkedFunction = this.circuitBreaker.decorateCheckedSupplier(() -> this.redisCache.putIfAbsent(key, value));
        Try<ValueWrapper> result = Try.of(checkedFunction).recoverWith(CallNotPermittedException.class, e -> this.recover(e));
        if (result.isFailure()) {
            Throwable cause = result.getCause();
            logger.log(loggerLevel, "Failure from RedisCache.putIfAbsent(): " + cause.getMessage());
        }
        return result.get();
    }
    
    @Override
    public void evict(Object key) {
    	this.logCBMetrics();
        Runnable decorateRunnable = this.circuitBreaker.decorateRunnable(() -> this.redisCache.evict(key));
        Try<Void> result = Try.runRunnable(decorateRunnable).recover(CallNotPermittedException.class, e -> this.recoverVoid(e));
        if (result.isFailure()) {
            Throwable cause = result.getCause();
            logger.log(loggerLevel, "Failure from RedisCache.evict(): " + cause.getMessage());
        }
        result.get();
    }
    
    @Override
    public void clear() {
    	this.logCBMetrics();
        Runnable decorateRunnable = this.circuitBreaker.decorateRunnable(() -> this.redisCache.clear());
        Try<Void> result = Try.runRunnable(decorateRunnable).recover(CallNotPermittedException.class, e -> this.recoverVoid(e));
        if (result.isFailure()) {
            Throwable cause = result.getCause();
            logger.log(loggerLevel, "Failure from RedisCache.clear(): " + cause.getMessage());
        }
        result.get();
    }

	public static void logCBMetrics(CircuitBreaker cb, Level loggerLevel) {
		Metrics cbMetrics = cb.getMetrics();
    	StringBuilder sb = new StringBuilder("CircuitBreaker Metrics for " + cb + ":\n");
    	sb.append("\tNum successful calls: ").append(cbMetrics.getNumberOfSuccessfulCalls()).append("\n");
    	sb.append("\tNum failed calls: ").append(cbMetrics.getNumberOfFailedCalls()).append("\n");
    	sb.append("\tNum slow calls: ").append(cbMetrics.getNumberOfSlowCalls()).append("\n");
    	sb.append("\tSlow call rate: ").append(cbMetrics.getSlowCallRate()).append("\n");
    	sb.append("\tNum not permitted calls: ").append(cbMetrics.getNumberOfNotPermittedCalls()).append("\n");
    	logger.log(loggerLevel, sb.toString());
	}

}
