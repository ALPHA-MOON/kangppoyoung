package com.policyfund.notices.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 번들된 python pipeline 을 서브프로세스로 실행해 공고 PDF 를 RAG 청크로 변환한다.
 * {@code python -m pipeline --input <pdf> --outdir <dir>} → {@code <dir>/chunks.jsonl}.
 * 파이프라인은 오프라인·결정론적(키 불필요)이라 동일 PDF → 동일 chunk_id 를 보장한다.
 */
@Component
@ConditionalOnProperty(name = "notices.reindex.chunker", havingValue = "pipeline", matchIfMissing = true)
public class PipelineNoticeChunker implements NoticeChunker {

    private static final Logger log = LoggerFactory.getLogger(PipelineNoticeChunker.class);

    private final String python;
    private final String pipelineDir;
    private final long timeoutSeconds;

    public PipelineNoticeChunker(
            @Value("${notices.reindex.python:${PIPELINE_PYTHON:python3}}") String python,
            @Value("${notices.reindex.pipeline-dir:.}") String pipelineDir,
            @Value("${notices.reindex.timeout-seconds:300}") long timeoutSeconds) {
        this.python = python;
        this.pipelineDir = pipelineDir;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public Path chunk(byte[] pdf) {
        Path tmp;
        try {
            tmp = Files.createTempDirectory("notice-reindex-");
        } catch (IOException e) {
            throw new UncheckedIOException("재색인 임시 디렉터리 생성 실패", e);
        }
        try {
            Path pdfPath = tmp.resolve("input.pdf");
            Files.write(pdfPath, pdf);
            Path outDir = tmp.resolve("out");
            Path logFile = tmp.resolve("pipeline.log");

            ProcessBuilder pb = new ProcessBuilder(
                    python, "-m", "pipeline",
                    "--input", pdfPath.toString(),
                    "--outdir", outDir.toString());
            pb.directory(new File(pipelineDir)); // `python -m pipeline` 가 pipeline 패키지를 찾도록 cwd 지정
            pb.redirectErrorStream(true);
            pb.redirectOutput(logFile.toFile()); // 파이프 버퍼 교착 방지 + 타임아웃 정상 동작

            Process proc = pb.start();
            boolean finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                throw new IllegalStateException("pipeline 타임아웃(" + timeoutSeconds + "s)");
            }
            int exit = proc.exitValue();
            if (exit != 0) {
                throw new IllegalStateException("pipeline 실패(exit=" + exit + "): " + tail(logFile));
            }
            Path jsonl = outDir.resolve("chunks.jsonl");
            if (!Files.isRegularFile(jsonl)) {
                throw new IllegalStateException("pipeline 산출물 chunks.jsonl 없음: " + jsonl);
            }
            return jsonl;
        } catch (IOException e) {
            throw new UncheckedIOException("pipeline 실행 실패", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("pipeline 대기 중단", e);
        }
    }

    /** 실패 진단용으로 로그 파일 끝부분만 추린다. */
    private static String tail(Path logFile) {
        try {
            String s = Files.exists(logFile)
                    ? new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8) : "";
            return s.length() <= 800 ? s : s.substring(s.length() - 800);
        } catch (IOException e) {
            return "(로그 읽기 실패)";
        }
    }
}
