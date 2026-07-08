package online.yudream.base.plugin.minecraft.application.service;

import online.yudream.base.plugin.minecraft.application.assembler.MinecraftServerAppAssembler;
import online.yudream.base.plugin.minecraft.application.cmd.MinecraftPlayerEventCmd;
import online.yudream.base.plugin.minecraft.application.cmd.MinecraftSeasonOpenCmd;
import online.yudream.base.plugin.minecraft.application.cmd.MinecraftServerSaveCmd;
import online.yudream.base.plugin.minecraft.application.dto.MinecraftEconomyRecordDTO;
import online.yudream.base.plugin.minecraft.application.dto.MinecraftSeasonOperationDTO;
import online.yudream.base.plugin.minecraft.application.dto.MinecraftPlayerActivityDTO;
import online.yudream.base.plugin.minecraft.application.dto.MinecraftServerDTO;
import online.yudream.base.plugin.minecraft.application.dto.MinecraftStatusSnapshotDTO;
import online.yudream.base.plugin.minecraft.domain.aggregate.MinecraftSeasonOperation;
import online.yudream.base.plugin.minecraft.domain.aggregate.MinecraftServer;
import online.yudream.base.plugin.minecraft.domain.aggregate.MinecraftPlayerActivity;
import online.yudream.base.plugin.minecraft.domain.enumerate.MinecraftEdition;
import online.yudream.base.plugin.minecraft.domain.enumerate.MinecraftSeasonOperationStatus;
import online.yudream.base.plugin.minecraft.domain.repo.MinecraftServerRepository;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftEndpointStatus;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftInheritanceRule;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftSeasonAdjustment;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftServerEndpoint;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftServerSeason;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftServerStatus;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftStatusSnapshot;
import online.yudream.base.plugin.minecraft.infrastructure.service.MinecraftStatusService;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletAsset;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletBalance;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletChangeRequest;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletService;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletTransaction;
import online.yudream.base.plugin.spi.system.wallet.PluginWalletTransactionQuery;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class MinecraftServerAppService {

    private static final long DEFAULT_HISTORY_WINDOW_MILLIS = 24L * 60 * 60 * 1000;
    private static final int DEFAULT_HISTORY_LIMIT = 144;
    private static final int MAX_HISTORY_LIMIT = 288;

    private final MinecraftServerRepository repository;
    private final MinecraftStatusService statusService;
    private final FrameworkServices framework;
    private final MinecraftServerAppAssembler assembler = new MinecraftServerAppAssembler();

    public MinecraftServerAppService(MinecraftServerRepository repository, MinecraftStatusService statusService, FrameworkServices framework) {
        this.repository = repository;
        this.statusService = statusService;
        this.framework = framework;
    }

    public List<MinecraftServerDTO> listServers(boolean includeDisabled, boolean refreshStatus) {
        return repository.list(1, 200, includeDisabled).stream()
                .map(server -> assembler.toDTO(server, refreshStatus ? refreshStatus(server.id()) : repository.findStatus(server.id()).orElse(null)))
                .toList();
    }

    public MinecraftServerDTO detail(String serverId, boolean refreshStatus) {
        MinecraftServer server = requireServer(serverId);
        MinecraftServerStatus status = refreshStatus ? refreshStatus(server.id()) : repository.findStatus(server.id()).orElse(null);
        return assembler.toDTO(server, status);
    }

    public MinecraftServerDTO saveServer(MinecraftServerSaveCmd cmd) {
        MinecraftServer existing = cmd.id() == null || cmd.id().isBlank()
                ? null
                : repository.findById(cmd.id()).orElse(null);
        List<MinecraftServerEndpoint> endpoints = toEndpoints(cmd.endpoints());
        List<MinecraftServerSeason> seasons = toSeasons(cmd.seasons());
        if (seasons.isEmpty()) {
            seasons = List.of(new MinecraftServerSeason(null, "第一周目", "初始周目", System.currentTimeMillis(), null, true, 0));
        }
        MinecraftServer server = existing == null
                ? MinecraftServer.create(cmd.name(), cmd.descriptionMarkdown(), cmd.enabled() == null || cmd.enabled(),
                cmd.sort() == null ? 0 : cmd.sort(), endpoints, seasons)
                : existing.update(cmd.name(), cmd.descriptionMarkdown(), cmd.enabled(), cmd.sort(), endpoints, seasons);
        MinecraftServer saved = repository.save(server);
        return assembler.toDTO(saved, repository.findStatus(saved.id()).orElse(null));
    }

    public void deleteServer(String serverId) {
        requireServer(serverId);
        repository.delete(serverId);
    }

    public MinecraftServerStatus refreshStatus(String serverId) {
        MinecraftServer server = requireServer(serverId);
        List<MinecraftEndpointStatus> statuses = server.endpoints().stream()
                .map(statusService::ping)
                .toList();
        MinecraftServerStatus status = repository.saveStatus(MinecraftServerStatus.from(server.id(), statuses));
        repository.saveStatusSnapshot(MinecraftStatusSnapshot.from(status));
        return status;
    }

    public void refreshEnabledServers() {
        for (MinecraftServer server : repository.list(1, 200, false)) {
            try {
                refreshStatus(server.id());
            } catch (RuntimeException ignored) {
            }
        }
    }

    public List<MinecraftStatusSnapshotDTO> statusHistory(String serverId, Long since, int limit) {
        requireServer(serverId);
        long from = since == null || since <= 0
                ? System.currentTimeMillis() - DEFAULT_HISTORY_WINDOW_MILLIS
                : normalizeTimestamp(since);
        int safeLimit = Math.max(Math.min(limit <= 0 ? DEFAULT_HISTORY_LIMIT : limit, MAX_HISTORY_LIMIT), 1);
        return repository.listStatusSnapshots(serverId, from, safeLimit).stream()
                .map(assembler::toDTO)
                .toList();
    }

    public boolean walletEnabled() {
        return walletOrNull() != null;
    }

    public MinecraftSeasonOperationDTO previewOpenSeason(String serverId, MinecraftSeasonOpenCmd cmd, String operatorUserId) {
        MinecraftSeasonOperation operation = buildSeasonOperation(requireServer(serverId), cmd, operatorUserId);
        return assembler.toDTO(operation, realIncomeTotals(wallet()));
    }

    public MinecraftSeasonOperationDTO openSeason(String serverId, MinecraftSeasonOpenCmd cmd, String operatorUserId) {
        MinecraftServer server = requireServer(serverId);
        MinecraftSeasonOperation preview = buildSeasonOperation(server, cmd, operatorUserId);
        List<MinecraftSeasonAdjustment> appliedAdjustments = applyAdjustments(preview);
        MinecraftSeasonOperation applied = repository.saveOperation(preview.applied(appliedAdjustments));
        repository.save(openSeasonOnServer(server, applied, cmd));
        return assembler.toDTO(applied, realIncomeTotals(wallet()));
    }

    public MinecraftSeasonOperationDTO rollbackSeasonOperation(String operationId, String operatorUserId) {
        MinecraftSeasonOperation operation = repository.findOperation(operationId)
                .orElseThrow(() -> new IllegalArgumentException("周目操作不存在：" + operationId));
        if (operation.status() != MinecraftSeasonOperationStatus.APPLIED) {
            throw new IllegalArgumentException("只有已应用的周目操作可以撤回");
        }
        MinecraftSeasonOperation latest = repository.listOperations(operation.serverId(), 1, 1).stream().findFirst().orElse(null);
        if (latest == null || !latest.id().equals(operation.id())) {
            throw new IllegalArgumentException("只能撤回该服务器最新一次周目操作");
        }
        List<MinecraftSeasonAdjustment> rolledBackAdjustments = rollbackAdjustments(operation);
        MinecraftSeasonOperation rolledBack = repository.saveOperation(operation.rolledBack(rolledBackAdjustments));
        repository.save(rollbackSeasonOnServer(requireServer(operation.serverId()), operation));
        return assembler.toDTO(rolledBack, realIncomeTotals(wallet()));
    }

    public List<MinecraftSeasonOperationDTO> operations(String serverId, int page, int size) {
        PluginWalletService wallet = walletOrNull();
        Map<String, BigDecimal> realIncomeTotals = wallet == null ? Map.of() : realIncomeTotals(wallet);
        return repository.listOperations(serverId, safePage(page), safeSize(size)).stream()
                .map(operation -> assembler.toDTO(operation, realIncomeTotals))
                .toList();
    }

    public List<MinecraftEconomyRecordDTO> userRecords(String serverId, String userId, int page, int size) {
        MinecraftServer server = requireServer(serverId);
        MinecraftServerSeason currentSeason = server.currentSeason();
        Long startAt = currentSeason == null ? null : currentSeason.startedAt();
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        int fetchSize = safePage * safeSize;
        PluginWalletService wallet = walletOrNull();
        List<MinecraftEconomyRecordDTO> walletRecords = wallet == null
                ? List.of()
                : wallet.transactions(new PluginWalletTransactionQuery(
                                null, null, null, requireText(userId, "用户不能为空"), startAt, null, 1, fetchSize
                        )).stream()
                        .map(transaction -> new MinecraftEconomyRecordDTO(transaction.id(), "WALLET", transaction.source(), transaction.type(),
                                transaction.assetCode(), transaction.amount(), transaction.businessNo(), transaction.remark(), transaction.createdAt()))
                        .toList();
        List<MinecraftEconomyRecordDTO> seasonRecords = repository.listOperations(serverId, 1, fetchSize).stream()
                .flatMap(operation -> operation.adjustments().stream()
                        .filter(adjustment -> userId.equals(adjustment.userId()))
                        .map(adjustment -> new MinecraftEconomyRecordDTO(
                                operation.id() + ":" + adjustment.assetCode(),
                                "SEASON_INHERIT",
                                "MINECRAFT_SEASON",
                                operation.status().name(),
                                adjustment.assetCode(),
                                adjustment.deltaAmount(),
                                adjustment.walletTransactionId(),
                                operation.toSeasonName() + " 继承处理：" + adjustment.ruleLabel(),
                                operation.createdAt()
                        )))
                .toList();
        return java.util.stream.Stream.concat(walletRecords.stream(), seasonRecords.stream())
                .sorted(Comparator.comparingLong(MinecraftEconomyRecordDTO::createdAt).reversed())
                .skip((long) (safePage - 1) * safeSize)
                .limit(safeSize)
                .toList();
    }

    public MinecraftPlayerActivityDTO recordJoin(String serverId, MinecraftPlayerEventCmd cmd) {
        requireServer(serverId);
        MinecraftPlayerActivity activity = activity(serverId, cmd).join(cmd.playerName(), eventAt(cmd));
        return assembler.toDTO(repository.savePlayerActivity(activity), System.currentTimeMillis());
    }

    public MinecraftPlayerActivityDTO recordQuit(String serverId, MinecraftPlayerEventCmd cmd) {
        requireServer(serverId);
        MinecraftPlayerActivity activity = activity(serverId, cmd).quit(cmd.playerName(), eventAt(cmd));
        return assembler.toDTO(repository.savePlayerActivity(activity), System.currentTimeMillis());
    }

    public MinecraftPlayerActivityDTO recordAfkStart(String serverId, MinecraftPlayerEventCmd cmd) {
        requireServer(serverId);
        MinecraftPlayerActivity activity = activity(serverId, cmd).startAfk(cmd.playerName(), eventAt(cmd));
        return assembler.toDTO(repository.savePlayerActivity(activity), System.currentTimeMillis());
    }

    public MinecraftPlayerActivityDTO recordAfkEnd(String serverId, MinecraftPlayerEventCmd cmd) {
        requireServer(serverId);
        MinecraftPlayerActivity activity = activity(serverId, cmd).endAfk(cmd.playerName(), eventAt(cmd));
        return assembler.toDTO(repository.savePlayerActivity(activity), System.currentTimeMillis());
    }

    public List<MinecraftPlayerActivityDTO> playerActivities(String serverId, int page, int size) {
        requireServer(serverId);
        long now = System.currentTimeMillis();
        return repository.listPlayerActivities(serverId, safePage(page), safeSize(size)).stream()
                .map(activity -> assembler.toDTO(activity, now))
                .toList();
    }

    private MinecraftSeasonOperation buildSeasonOperation(MinecraftServer server, MinecraftSeasonOpenCmd cmd, String operatorUserId) {
        long startedAt = cmd.startedAt() == null ? System.currentTimeMillis() : cmd.startedAt();
        MinecraftServerSeason currentSeason = server.currentSeason();
        if (currentSeason != null && currentSeason.startedAt() != null && startedAt <= currentSeason.startedAt()) {
            throw new IllegalArgumentException("新周目开始时间必须晚于当前周目开始时间");
        }
        List<MinecraftInheritanceRule> rules = normalizeRules(cmd.rules());
        String toSeasonId = UUID.randomUUID().toString();
        List<MinecraftSeasonAdjustment> adjustments = calculateAdjustments(server, currentSeason, startedAt, rules);
        return MinecraftSeasonOperation.preview(
                server.id(),
                currentSeason == null ? null : currentSeason.id(),
                toSeasonId,
                requireText(cmd.name(), "新周目名称不能为空"),
                rules,
                adjustments,
                operatorUserId,
                cmd.remark()
        );
    }

    private List<MinecraftSeasonAdjustment> calculateAdjustments(MinecraftServer server, MinecraftServerSeason currentSeason,
                                                                 long endAt, List<MinecraftInheritanceRule> rules) {
        PluginWalletService wallet = wallet();
        Map<String, PluginWalletAsset> assets = wallet.assets().stream()
                .collect(Collectors.toMap(PluginWalletAsset::code, asset -> asset, (first, second) -> first, LinkedHashMap::new));
        Map<String, BigDecimal> balances = balances(wallet, assets);
        Map<String, BigDecimal> seasonIncome = incomeTotals(wallet, currentSeason == null ? null : currentSeason.startedAt(), endAt);
        Map<String, BigDecimal> inherited = inheritedTotals(server.id(), currentSeason == null ? null : currentSeason.id());

        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.addAll(inherited.keySet());
        keys.addAll(seasonIncome.keySet());

        return keys.stream()
                .map(key -> {
                    String[] parts = key.split(":", 2);
                    String userId = parts[0];
                    String assetCode = parts[1];
                    PluginWalletAsset asset = assets.get(assetCode);
                    if (asset == null) {
                        return null;
                    }
                    BigDecimal inheritedAmount = inherited.getOrDefault(key, BigDecimal.ZERO);
                    BigDecimal incomeAmount = seasonIncome.getOrDefault(key, BigDecimal.ZERO);
                    BigDecimal seasonTotal = inheritedAmount.add(incomeAmount);
                    MinecraftInheritanceRule rule = matchRule(rules, assetCode, seasonTotal);
                    BigDecimal nextInherited = scale(seasonTotal.multiply(rule.inheritRate()), asset.scale());
                    BigDecimal walletBalance = balances.getOrDefault(key, BigDecimal.ZERO.setScale(asset.scale()));
                    BigDecimal delta = nextInherited.subtract(walletBalance);
                    return new MinecraftSeasonAdjustment(userId, assetCode, inheritedAmount, incomeAmount, seasonTotal,
                            nextInherited, walletBalance, delta, direction(delta), "可使用 " + rule.assetPattern() + " " + rule.rangeLabel() + " x " + rule.inheritRate(), null, null);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(MinecraftSeasonAdjustment::userId).thenComparing(MinecraftSeasonAdjustment::assetCode))
                .toList();
    }

    private List<MinecraftSeasonAdjustment> applyAdjustments(MinecraftSeasonOperation operation) {
        PluginWalletService wallet = wallet();
        List<MinecraftSeasonAdjustment> applied = new ArrayList<>();
        try {
            for (MinecraftSeasonAdjustment adjustment : operation.adjustments()) {
                BigDecimal delta = adjustment.deltaAmount();
                if (delta == null || delta.signum() == 0) {
                    applied.add(adjustment);
                    continue;
                }
                String businessNo = "mc-season:" + operation.id() + ":" + adjustment.userId() + ":" + adjustment.assetCode();
                PluginWalletTransaction transaction = delta.signum() > 0
                        ? wallet.credit(new PluginWalletChangeRequest(adjustment.userId(), adjustment.assetCode(), delta, businessNo, resetRemark(operation, adjustment)))
                        : wallet.debit(new PluginWalletChangeRequest(adjustment.userId(), adjustment.assetCode(), delta.abs(), businessNo, resetRemark(operation, adjustment)));
                applied.add(adjustment.withWalletTransaction(transaction.id()));
            }
            return List.copyOf(applied);
        } catch (RuntimeException e) {
            rollbackPartialAdjustments(operation, applied, e);
            throw e;
        }
    }

    private void rollbackPartialAdjustments(MinecraftSeasonOperation operation, List<MinecraftSeasonAdjustment> applied, RuntimeException original) {
        PluginWalletService wallet = wallet();
        for (MinecraftSeasonAdjustment adjustment : applied) {
            if (adjustment.walletTransactionId() == null || adjustment.deltaAmount() == null || adjustment.deltaAmount().signum() == 0) {
                continue;
            }
            try {
                BigDecimal amount = adjustment.deltaAmount().abs();
                String businessNo = "mc-season-partial-rollback:" + operation.id() + ":" + adjustment.userId() + ":" + adjustment.assetCode();
                if (adjustment.deltaAmount().signum() > 0) {
                    wallet.debit(new PluginWalletChangeRequest(adjustment.userId(), adjustment.assetCode(), amount, businessNo, "周目继承失败自动撤回"));
                } else {
                    wallet.credit(new PluginWalletChangeRequest(adjustment.userId(), adjustment.assetCode(), amount, businessNo, "周目继承失败自动撤回"));
                }
            } catch (RuntimeException rollbackError) {
                original.addSuppressed(rollbackError);
            }
        }
    }

    private List<MinecraftSeasonAdjustment> rollbackAdjustments(MinecraftSeasonOperation operation) {
        PluginWalletService wallet = wallet();
        List<MinecraftSeasonAdjustment> results = new ArrayList<>();
        for (MinecraftSeasonAdjustment adjustment : operation.adjustments()) {
            if (adjustment.walletTransactionId() == null || adjustment.deltaAmount() == null || adjustment.deltaAmount().signum() == 0) {
                results.add(adjustment);
                continue;
            }
            BigDecimal amount = adjustment.deltaAmount().abs();
            String businessNo = "mc-season-rollback:" + operation.id() + ":" + adjustment.userId() + ":" + adjustment.assetCode();
            PluginWalletTransaction transaction = adjustment.deltaAmount().signum() > 0
                    ? wallet.debit(new PluginWalletChangeRequest(adjustment.userId(), adjustment.assetCode(), amount, businessNo, "撤回周目继承入账：" + operation.toSeasonName()))
                    : wallet.credit(new PluginWalletChangeRequest(adjustment.userId(), adjustment.assetCode(), amount, businessNo, "撤回周目继承扣账：" + operation.toSeasonName()));
            results.add(adjustment.withRollbackTransaction(transaction.id()));
        }
        return List.copyOf(results);
    }

    private MinecraftPlayerActivity activity(String serverId, MinecraftPlayerEventCmd cmd) {
        String playerId = requireText(cmd.playerId(), "玩家 ID 不能为空");
        long eventAt = eventAt(cmd);
        return repository.findPlayerActivity(serverId, playerId)
                .orElseGet(() -> MinecraftPlayerActivity.empty(serverId, playerId, cmd.playerName(), eventAt));
    }

    private long eventAt(MinecraftPlayerEventCmd cmd) {
        Long value = cmd.eventAt();
        return normalizeTimestamp(value);
    }

    private long normalizeTimestamp(Long value) {
        long now = System.currentTimeMillis();
        if (value == null || value <= 0) {
            return now;
        }
        return value < 10_000_000_000L ? value * 1000 : value;
    }

    private String resetRemark(MinecraftSeasonOperation operation, MinecraftSeasonAdjustment adjustment) {
        return "周目余额重置：" + operation.toSeasonName()
                + "，本周目可使用 " + adjustment.seasonTotalAmount()
                + "，下周目可使用 " + adjustment.nextInheritedAmount();
    }

    private MinecraftServer openSeasonOnServer(MinecraftServer server, MinecraftSeasonOperation operation, MinecraftSeasonOpenCmd cmd) {
        long startedAt = cmd.startedAt() == null ? System.currentTimeMillis() : cmd.startedAt();
        List<MinecraftServerSeason> nextSeasons = new ArrayList<>();
        for (MinecraftServerSeason season : server.seasons()) {
            if (season.id().equals(operation.fromSeasonId())) {
                nextSeasons.add(new MinecraftServerSeason(season.id(), season.name(), season.description(), season.startedAt(), startedAt, false, season.sort()));
            } else {
                nextSeasons.add(season.withCurrent(false));
            }
        }
        int nextSort = nextSeasons.stream().mapToInt(MinecraftServerSeason::sort).max().orElse(0) + 10;
        nextSeasons.add(new MinecraftServerSeason(operation.toSeasonId(), operation.toSeasonName(), cmd.description(), startedAt, null, true, nextSort));
        return server.update(null, null, null, null, null, nextSeasons);
    }

    private MinecraftServer rollbackSeasonOnServer(MinecraftServer server, MinecraftSeasonOperation operation) {
        List<MinecraftServerSeason> nextSeasons = server.seasons().stream()
                .filter(season -> !season.id().equals(operation.toSeasonId()))
                .map(season -> season.id().equals(operation.fromSeasonId())
                        ? new MinecraftServerSeason(season.id(), season.name(), season.description(), season.startedAt(), null, true, season.sort())
                        : season.withCurrent(false))
                .toList();
        return server.update(null, null, null, null, null, nextSeasons);
    }

    private Map<String, BigDecimal> balances(PluginWalletService wallet, Map<String, PluginWalletAsset> assets) {
        Map<String, BigDecimal> results = new HashMap<>();
        for (String assetCode : assets.keySet()) {
            for (PluginWalletBalance balance : wallet.listBalances(assetCode, 1, 5000)) {
                results.put(key(balance.userId(), balance.assetCode()), balance.balance());
            }
        }
        return results;
    }

    private Map<String, BigDecimal> incomeTotals(PluginWalletService wallet, Long startAt, Long endAt) {
        return walletTransactions(wallet, startAt, endAt).stream()
                .filter(this::isSeasonIncomeSource)
                .collect(Collectors.groupingBy(transaction -> key(transaction.toUserId(), transaction.assetCode()),
                        Collectors.mapping(PluginWalletTransaction::amount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
    }

    private List<PluginWalletTransaction> walletTransactions(PluginWalletService wallet, Long startAt, Long endAt) {
        List<PluginWalletTransaction> results = new ArrayList<>();
        int page = 1;
        int size = 5000;
        while (true) {
            List<PluginWalletTransaction> items = wallet.transactions(new PluginWalletTransactionQuery(null, "CREDIT", null, null, startAt, endAt, page, size));
            results.addAll(items);
            if (items.size() < size) {
                return results;
            }
            page++;
        }
    }

    private Map<String, BigDecimal> realIncomeTotals(PluginWalletService wallet) {
        return incomeTotals(wallet, null, null);
    }

    private Map<String, BigDecimal> inheritedTotals(String serverId, String seasonId) {
        if (seasonId == null) {
            return Map.of();
        }
        return repository.listOperations(serverId, 1, 500).stream()
                .filter(operation -> operation.status() == MinecraftSeasonOperationStatus.APPLIED)
                .filter(operation -> seasonId.equals(operation.toSeasonId()))
                .findFirst()
                .map(operation -> operation.adjustments().stream()
                        .collect(Collectors.toMap(
                                adjustment -> key(adjustment.userId(), adjustment.assetCode()),
                                MinecraftSeasonAdjustment::nextInheritedAmount,
                                (first, second) -> second
                        )))
                .orElse(Map.of());
    }

    private MinecraftInheritanceRule matchRule(List<MinecraftInheritanceRule> rules, String assetCode, BigDecimal amount) {
        return rules.stream()
                .filter(rule -> rule.matchesAsset(assetCode))
                .sorted(Comparator.comparingInt(rule -> specificity(rule.assetPattern())))
                .filter(rule -> rule.matchesAmount(amount))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("币种 " + assetCode + " 的累计金额 " + amount + " 未匹配继承规则，请补充覆盖该金额区间的通配规则"));
    }

    private int specificity(String pattern) {
        return pattern == null || pattern.contains("*") ? 10 : 0;
    }

    private boolean isSeasonIncomeSource(PluginWalletTransaction transaction) {
        return !"MINECRAFT_SEASON".equals(transaction.source());
    }

    private List<MinecraftInheritanceRule> normalizeRules(List<MinecraftSeasonOpenCmd.Rule> rules) {
        List<MinecraftInheritanceRule> result = (rules == null || rules.isEmpty() ? defaultRules() : rules.stream()
                .map(rule -> new MinecraftInheritanceRule(rule.assetPattern(), rule.minAmount(), rule.maxAmount(), rule.inheritRate()))
                .toList());
        validateRuleCoverage(result);
        return result;
    }

    private List<MinecraftInheritanceRule> defaultRules() {
        return List.of(
                new MinecraftInheritanceRule("*", BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("0.5")),
                new MinecraftInheritanceRule("*", new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("0.6")),
                new MinecraftInheritanceRule("*", new BigDecimal("200"), new BigDecimal("350"), new BigDecimal("0.75")),
                new MinecraftInheritanceRule("*", new BigDecimal("350"), new BigDecimal("500"), new BigDecimal("0.85")),
                new MinecraftInheritanceRule("*", new BigDecimal("500"), null, new BigDecimal("0.9"))
        );
    }

    private void validateRuleCoverage(List<MinecraftInheritanceRule> rules) {
        Map<String, List<MinecraftInheritanceRule>> groups = rules.stream()
                .collect(Collectors.groupingBy(MinecraftInheritanceRule::assetPattern));
        for (Map.Entry<String, List<MinecraftInheritanceRule>> entry : groups.entrySet()) {
            List<MinecraftInheritanceRule> items = entry.getValue().stream()
                    .sorted(Comparator.comparing(MinecraftInheritanceRule::minAmount))
                    .toList();
            BigDecimal cursor = BigDecimal.ZERO;
            for (MinecraftInheritanceRule rule : items) {
                if (rule.minAmount().compareTo(cursor) != 0) {
                    throw new IllegalArgumentException("币种通配 " + entry.getKey() + " 的继承区间不连续，缺少从 " + cursor + " 开始的规则");
                }
                if (rule.maxAmount() == null) {
                    cursor = null;
                    break;
                }
                cursor = rule.maxAmount();
            }
            if (cursor != null) {
                throw new IllegalArgumentException("币种通配 " + entry.getKey() + " 缺少无上限继承规则");
            }
        }
        if (rules.stream().noneMatch(rule -> "*".equals(rule.assetPattern()))) {
            throw new IllegalArgumentException("至少需要一组 * 通配规则用于兜底处理所有币种");
        }
    }

    private List<MinecraftServerEndpoint> toEndpoints(List<MinecraftServerSaveCmd.Endpoint> endpoints) {
        if (endpoints == null) {
            return List.of();
        }
        return endpoints.stream()
                .map(item -> new MinecraftServerEndpoint(item.id(), item.name(), item.host(), item.port() == null ? 0 : item.port(),
                        MinecraftEdition.of(item.edition()), Boolean.TRUE.equals(item.primaryLine()),
                        item.enabled() == null || item.enabled(), item.sort() == null ? 0 : item.sort()))
                .toList();
    }

    private List<MinecraftServerSeason> toSeasons(List<MinecraftServerSaveCmd.Season> seasons) {
        if (seasons == null) {
            return List.of();
        }
        return seasons.stream()
                .map(item -> new MinecraftServerSeason(item.id(), item.name(), item.description(), item.startedAt(), item.endedAt(),
                        Boolean.TRUE.equals(item.current()), item.sort() == null ? 0 : item.sort()))
                .toList();
    }

    private MinecraftServer requireServer(String serverId) {
        return repository.findById(requireText(serverId, "服务器 ID 不能为空"))
                .orElseThrow(() -> new IllegalArgumentException("服务器不存在：" + serverId));
    }

    private PluginWalletService wallet() {
        return framework.extension("yudream-wallet", PluginWalletService.class)
                .orElseThrow(() -> new IllegalStateException("钱包插件未启用，无法执行周目货币继承"));
    }

    private PluginWalletService walletOrNull() {
        return framework.extension("yudream-wallet", PluginWalletService.class).orElse(null);
    }

    private BigDecimal scale(BigDecimal value, int scale) {
        return value.setScale(Math.max(scale, 0), RoundingMode.DOWN);
    }

    private String direction(BigDecimal delta) {
        if (delta.signum() > 0) {
            return "CREDIT";
        }
        if (delta.signum() < 0) {
            return "DEBIT";
        }
        return "NONE";
    }

    private int safePage(int page) {
        return Math.max(page, 1);
    }

    private int safeSize(int size) {
        return Math.max(Math.min(size, 100), 1);
    }

    private String key(String userId, String assetCode) {
        return requireText(userId, "用户不能为空") + ":" + requireText(assetCode, "币种不能为空");
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
