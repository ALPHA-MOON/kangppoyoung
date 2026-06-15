package com.policyfund.search.service;

import com.policyfund.common.error.ConflictException;
import com.policyfund.common.error.ResourceNotFoundException;
import com.policyfund.search.domain.SearchExampleEntity;
import com.policyfund.search.domain.SearchExampleRepository;
import com.policyfund.search.dto.SearchExample;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SearchExampleService {

    private static final int MAX = 5;
    private final SearchExampleRepository repo;

    public SearchExampleService(SearchExampleRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<SearchExample> list() {
        return repo.findAllByOrderBySlotAsc().stream()
                .map(e -> new SearchExample(String.valueOf(e.getId()), e.getText()))
                .toList();
    }

    @Transactional
    public SearchExample add(String text) {
        List<SearchExampleEntity> existing = repo.findAllByOrderBySlotAsc();
        if (existing.size() >= MAX) {
            throw new ConflictException("EXAMPLE_LIMIT", "예시 질문은 최대 " + MAX + "개까지 등록할 수 있습니다.");
        }
        Set<Byte> used = existing.stream().map(SearchExampleEntity::getSlot).collect(Collectors.toSet());
        byte slot = 0;
        while (used.contains(slot)) slot++;
        try {
            SearchExampleEntity saved = repo.saveAndFlush(new SearchExampleEntity(slot, text, Instant.now()));
            return new SearchExample(String.valueOf(saved.getId()), saved.getText());
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("EXAMPLE_LIMIT", "예시 질문은 최대 " + MAX + "개까지 등록할 수 있습니다.");
        }
    }

    @Transactional
    public void delete(String exampleId) {
        long id;
        try {
            id = Long.parseLong(exampleId);
        } catch (NumberFormatException e) {
            throw new ResourceNotFoundException("EXAMPLE_NOT_FOUND", "예시 질문을 찾을 수 없습니다: " + exampleId);
        }
        if (!repo.existsById(id)) {
            throw new ResourceNotFoundException("EXAMPLE_NOT_FOUND", "예시 질문을 찾을 수 없습니다: " + exampleId);
        }
        repo.deleteById(id);
    }
}
