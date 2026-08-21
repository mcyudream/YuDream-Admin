package online.yudream.base.infra.platform.milky.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxCase;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxCaseRepo;
import online.yudream.base.infra.platform.plugin.service.PluginDevModeProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * QQ 沙盒用例的本地 JSON 仓储：文件默认 plugins/qq-sandbox-cases.json，
 * 与开发模式项目清单同目录（相对 user.dir）。用例量小且访问低频，读直读文件、
 * 写采用 tmp + ATOMIC_MOVE 与 {@code PluginDevProjectCatalog} 同一持久化约定。
 */
@Slf4j
@Component
public class JsonQqSandboxCaseRepo implements QqSandboxCaseRepo {

    private final PluginDevModeProperties properties;
    private final ObjectMapper objectMapper;

    public JsonQqSandboxCaseRepo(PluginDevModeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<QqSandboxCase> findAll() {
        List<QqSandboxCase> cases = load(storeFile());
        cases.sort(Comparator.comparing(QqSandboxCase::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return cases;
    }

    @Override
    public Optional<QqSandboxCase> findById(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        String target = id.trim();
        return load(storeFile()).stream().filter(item -> target.equals(item.id())).findFirst();
    }

    @Override
    public synchronized void save(QqSandboxCase sandboxCase) {
        List<QqSandboxCase> cases = load(storeFile());
        cases.removeIf(item -> item.id().equals(sandboxCase.id()));
        cases.add(sandboxCase);
        persist(storeFile(), cases);
    }

    @Override
    public synchronized void delete(String id) {
        if (!StringUtils.hasText(id)) {
            return;
        }
        String target = id.trim();
        List<QqSandboxCase> cases = load(storeFile());
        if (cases.removeIf(item -> target.equals(item.id()))) {
            persist(storeFile(), cases);
        }
    }

    private Path storeFile() {
        return properties.resolvedStoreFile().resolveSibling("qq-sandbox-cases.json");
    }

    private List<QqSandboxCase> load(Path file) {
        if (!Files.isRegularFile(file)) {
            return new ArrayList<>();
        }
        try {
            CaseStore store = objectMapper.readValue(file.toFile(), CaseStore.class);
            List<QqSandboxCase> result = new ArrayList<>();
            if (store.cases != null) {
                for (QqSandboxCase item : store.cases) {
                    if (item != null && StringUtils.hasText(item.id())) {
                        result.add(item);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("QQ 沙盒用例清单读取失败，按空清单处理：{}：{}", file, e.getMessage());
            return new ArrayList<>();
        }
    }

    private void persist(Path file, List<QqSandboxCase> cases) {
        try {
            Files.createDirectories(file.getParent());
            CaseStore store = new CaseStore();
            store.version = 1;
            store.cases = List.copyOf(cases);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), store);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BizException("QQ 沙盒用例清单写入失败：" + e.getMessage());
        }
    }

    /** 清单文件结构：{"version":1,"cases":[...]} */
    @SuppressWarnings("unused")
    private static final class CaseStore {
        public int version;
        public List<QqSandboxCase> cases;
    }
}
