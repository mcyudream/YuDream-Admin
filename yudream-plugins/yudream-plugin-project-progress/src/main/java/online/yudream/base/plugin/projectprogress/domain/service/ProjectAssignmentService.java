package online.yudream.base.plugin.projectprogress.domain.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class ProjectAssignmentService {

    private final SecureRandom random = new SecureRandom();

    public List<String> randomAssignees(List<String> candidates, int requiredCount) {
        List<String> pool = normalize(candidates);
        int count = Math.max(requiredCount, 1);
        if (pool.size() < count) {
            throw new IllegalArgumentException("可分配用户数量不足，无法随机分配");
        }
        List<String> shuffled = new ArrayList<>(pool);
        java.util.Collections.shuffle(shuffled, random);
        return List.copyOf(shuffled.subList(0, count));
    }

    private List<String> normalize(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
    }
}
