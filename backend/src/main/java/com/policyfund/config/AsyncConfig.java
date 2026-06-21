package com.policyfund.config;

import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** 개정본 등록 후 RAG 재색인을 백그라운드로 돌리기 위한 비동기 실행기. */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "reindexExecutor")
    public Executor reindexExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("reindex-");
        // 큐 포화 시 제출 예외가 등록 스레드로 전파되지 않도록 폐기+로깅(검색은 다음 등록 시 갱신).
        executor.setRejectedExecutionHandler((r, exec) ->
                LoggerFactory.getLogger(AsyncConfig.class)
                        .warn("RAG 재색인 큐 포화 — 작업 폐기(검색은 다음 등록 시 갱신됨)"));
        executor.initialize();
        return executor;
    }
}
