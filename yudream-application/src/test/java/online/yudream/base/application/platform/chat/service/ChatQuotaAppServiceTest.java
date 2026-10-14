package online.yudream.base.application.platform.chat.service;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.chat.aggregate.UserChatQuota;
import online.yudream.base.domain.platform.chat.repo.ChatQuotaRepo;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class ChatQuotaAppServiceTest {
    @Test
    void concurrentBudgetsShareReservedTokensAndReleaseUnusedBudget() {
        MemoryQuota repo = new MemoryQuota();
        var service = service(repo);
        var first=service.reserve(7L,80); var second=service.reserve(7L,80);
        assertEquals(80, first.reservedTokens()); assertEquals(20, second.reservedTokens());
        assertThrows(BizException.class, ()->service.reserve(7L,1));
        service.settle(first,new AiUsage(10,20,30));
        assertEquals(50,service.me(7L).remainingTokens());
        service.release(second);
        assertEquals(70,service.me(7L).remainingTokens());
        assertEquals(0,service.me(7L).reservedTokens());
    }
    @Test
    void settlementIsIdempotentAndAccuratelyRecordsOverageWithoutDiscardingResponse() {
        MemoryQuota repo = new MemoryQuota(); var service=service(repo);
        var reservation=service.reserve(7L,100);
        service.settle(reservation,new AiUsage(90,40,130));
        service.settle(reservation,new AiUsage(90,40,130));
        assertEquals(130,service.me(7L).usedTokens());
        assertEquals(0,service.me(7L).remainingTokens());
        assertThrows(BizException.class, ()->service.reserve(7L,1));
    }
    @Test
    void unboundSystemJobsUseSeparateFiniteBudgetAndSettleOriginalAccountingDay() {
        MemoryQuota repo = new MemoryQuota(); var service=service(repo);
        var system=service.reserve(null,1000);
        assertEquals(0L,system.userId()); assertEquals(40,system.reservedTokens());
        var user=service.reserve(7L,20);
        assertEquals(100,service.me(7L).limitTokens());
        service.settle(system,new AiUsage(5,10,15));
        assertEquals(system.usageDate(),repo.lastSettledDate);
        service.release(user);
        assertEquals(25,service.reserve(0L,1000).reservedTokens());
    }
    @Test
    void crashedLeaseOnlyReleasesBudgetAndLateActualUsageCanBeChargedOnce() {
        MemoryQuota repo = new MemoryQuota(); var first = service(repo);
        var reservation = first.reserve(7L,80);
        repo.expire(reservation.id());
        var restarted = service(repo);
        assertEquals(100,restarted.me(7L).remainingTokens());
        assertEquals(0,restarted.me(7L).usedTokens());
        first.settle(reservation,new AiUsage(10,20,30));
        restarted.settle(reservation,new AiUsage(10,20,30));
        assertEquals(30,restarted.me(7L).usedTokens());
        assertEquals(70,restarted.me(7L).remainingTokens());
    }
    @Test
    void activeOwnerHeartbeatRenewsLeaseBeforeAnotherInstanceReclaimsIt() {
        MemoryQuota repo = new MemoryQuota(); var first = service(repo);
        var reservation = first.reserve(7L,80);repo.expire(reservation.id());
        first.renewActiveReservations();
        assertEquals(20,service(repo).me(7L).remainingTokens());
        first.release(reservation);
        assertEquals(100,service(repo).me(7L).remainingTokens());
    }
    @Test
    void crashAfterModelStartsRetainsUnknownBudgetUntilActualUsageIsReconciled() {
        MemoryQuota repo = new MemoryQuota(); var first = service(repo);
        var reservation = first.reserve(7L,80);
        first.markStarted(reservation); repo.expire(reservation.id());
        var restarted = service(repo);
        assertEquals(20,restarted.me(7L).remainingTokens());
        assertEquals(0,restarted.me(7L).usedTokens());
        assertEquals(80,restarted.me(7L).uncertainTokens());
        assertEquals(reservation.id(),restarted.unresolved(7L,null,reservation.usageDate()).getFirst().reservationId());
        var command = new online.yudream.base.application.platform.chat.cmd.ChatQuotaReconcileCmd("7",reservation.usageDate(),reservation.id(),new AiUsage(10,20,30));
        restarted.reconcile(command);
        assertThrows(BizException.class,()->restarted.reconcile(command));
        assertEquals(30,restarted.me(7L).usedTokens()); assertEquals(0,restarted.me(7L).uncertainTokens());
        assertEquals(70,restarted.me(7L).remainingTokens());
    }
    @Test
    void queryingAnEarlierAccountingDayReclaimsExpiredLeasesAndExposesOnlyUnknownConsumption() {
        MemoryQuota repo = new MemoryQuota();
        LocalDate priorDay = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(1);
        repo.getOrCreate(7L, priorDay, 100);
        var expiry = java.time.Instant.now().minusSeconds(121);
        repo.reserve(7L, priorDay, "old-started", 80, "old-run", expiry);
        repo.reserve(7L, priorDay, "old-unstarted", 20, "old-unused-run", expiry);
        repo.markStarted(7L, priorDay, "old-started");
        var restarted = service(repo);
        var unresolved = restarted.unresolved(7L, null, priorDay);
        assertEquals(1, unresolved.size());
        assertEquals("old-started", unresolved.getFirst().reservationId());
        assertEquals(priorDay, unresolved.getFirst().usageDate()); assertEquals(80, unresolved.getFirst().heldTokens());
        var previousQuota = repo.findByUserAndDate(7L, priorDay).orElseThrow();
        assertEquals(0, previousQuota.getReservedTokens()); assertEquals(80, previousQuota.getUncertainTokens());
        assertEquals(0, previousQuota.getUsedTokens());
        assertEquals(0, repo.receipts.get("old-unstarted"));
        assertEquals(1, repo.quotas.size());
        assertEquals(unresolved, restarted.unresolved(7L, null, priorDay));
        assertEquals(80, previousQuota.getUncertainTokens());
    }

    @Test
    void cancellingWithoutProviderUsageCannotRepeatedlyReleaseStartedBudget() {
        MemoryQuota repo = new MemoryQuota(); var service = service(repo);
        var first = service.reserve(7L,80);
        service.markStarted(first); service.uncertain(first); service.uncertain(first);
        assertEquals(80,service.me(7L).uncertainTokens());
        var second = service.reserve(7L,80);
        assertEquals(20,second.reservedTokens()); service.markStarted(second); service.uncertain(second);
        assertThrows(BizException.class,()->service.reserve(7L,1));
        assertEquals(0,service.me(7L).usedTokens()); assertEquals(100,service.me(7L).uncertainTokens());
    }
    @Test
    void knownPartialUsageIsChargedImmediatelyAndFinalReconciliationOnlyChargesDifference() {
        MemoryQuota repo = new MemoryQuota(); var service = service(repo);
        var reservation = service.reserve(7L,80);
        service.uncertain(reservation,new AiUsage(10,20,30));
        service.uncertain(reservation,new AiUsage(10,20,30));
        assertEquals(30,service.me(7L).usedTokens()); assertEquals(50,service.me(7L).uncertainTokens());
        assertEquals(20,service.me(7L).remainingTokens());
        assertEquals(30,service.unresolved(7L,null,reservation.usageDate()).getFirst().knownTokens());
        assertThrows(BizException.class,()->service.reconcile(new online.yudream.base.application.platform.chat.cmd.ChatQuotaReconcileCmd(
                "7",reservation.usageDate(),reservation.id(),new AiUsage(10,10,20))));
        service.reconcile(new online.yudream.base.application.platform.chat.cmd.ChatQuotaReconcileCmd(
                "7",reservation.usageDate(),reservation.id(),new AiUsage(15,30,45)));
        service.settle(reservation,new AiUsage(15,30,45));
        assertEquals(45,service.me(7L).usedTokens()); assertEquals(0,service.me(7L).uncertainTokens());
        assertEquals(55,service.me(7L).remainingTokens());
    }
    private static ChatQuotaAppService service(MemoryQuota repo){
        CapabilityAppService gate=new CapabilityAppService(null,List.of()){@Override public void ensureEnabled(String code,String name){}};
        CapabilityModule module=CapabilityModule.builder().code("ai").config(Map.of("dailyTokenLimit","100","systemDailyTokenLimit","40")).build();
        CapabilityModuleRepo modules=(CapabilityModuleRepo) Proxy.newProxyInstance(CapabilityModuleRepo.class.getClassLoader(),
                new Class<?>[]{CapabilityModuleRepo.class},(proxy,method,args)->Optional.of(module));
        var runs=(online.yudream.base.domain.platform.ai.repo.AiRunRepo) Proxy.newProxyInstance(ChatQuotaAppServiceTest.class.getClassLoader(),
                new Class<?>[]{online.yudream.base.domain.platform.ai.repo.AiRunRepo.class},(proxy,method,args)->Optional.empty());
        return new ChatQuotaAppService(gate,modules,repo,runs);
    }
    private static class MemoryQuota implements ChatQuotaRepo{
        final Map<String,UserChatQuota> quotas=new HashMap<>();
        final Map<String,online.yudream.base.domain.platform.ai.valobj.AiUsageReservation> reservations=new HashMap<>();
        final Map<String,Long> receipts=new HashMap<>();LocalDate lastSettledDate;
        final Map<String,online.yudream.base.domain.platform.ai.valobj.AiUsageReservation> uncertain=new HashMap<>();
        public synchronized UserChatQuota save(UserChatQuota q){quotas.put(q.getUserId()+"/"+q.getUsageDate(),q);return q;}
        public synchronized Optional<UserChatQuota> findByUserAndDate(Long user,LocalDate day){return Optional.ofNullable(quotas.get(user+"/"+day));}
        public synchronized UserChatQuota getOrCreate(Long user,LocalDate day,long limit){
            var quota=quotas.computeIfAbsent(user+"/"+day,key->UserChatQuota.of(user,day,limit));quota.setLimitTokens(limit);return quota;
        }
        public synchronized Optional<UserChatQuota> addUsage(Long user,LocalDate day,long tokens){return Optional.empty();}
        public synchronized Optional<UserChatQuota> reserve(Long user,LocalDate day,String id,long tokens,String runId,java.time.Instant lease){
            var quota=quotas.get(user+"/"+day);if(quota.remaining()<tokens)return Optional.empty();
            quota.setReservedTokens(quota.getReservedTokens()+tokens);
            reservations.put(id,new online.yudream.base.domain.platform.ai.valobj.AiUsageReservation(id,user,day,tokens,runId,lease));return Optional.of(quota);
        }
        public synchronized Optional<UserChatQuota> settle(Long user,LocalDate day,String id,long reserved,long used){
            var quota=quotas.get(user+"/"+day);lastSettledDate=day;
            if(reservations.remove(id)!=null){quota.setReservedTokens(quota.getReservedTokens()-reserved);quota.setUsedTokens(quota.getUsedTokens()+used);receipts.put(id,used);return Optional.of(quota);}
            var hold=uncertain.get(id);if(hold!=null&&used>=hold.knownTokens()){uncertain.remove(id);quota.setUncertainTokens(quota.getUncertainTokens()-hold.reservedTokens());quota.setUsedTokens(quota.getUsedTokens()+used-hold.knownTokens());receipts.put(id,used);return Optional.of(quota);}
            if(used>0&&receipts.getOrDefault(id,-1L)==0){quota.setUsedTokens(quota.getUsedTokens()+used);receipts.put(id,used);return Optional.of(quota);}
            return Optional.empty();
        }
        public synchronized List<online.yudream.base.domain.platform.ai.valobj.AiUsageReservation> reservations(Long user,LocalDate date){
            return reservations.values().stream().filter(r->r.userId().equals(user)&&r.usageDate().equals(date)).toList();
        }
        public synchronized void renew(Long user,LocalDate date,String id,java.time.Instant expiry){
            var old=reservations.get(id);if(old!=null)reservations.put(id,new online.yudream.base.domain.platform.ai.valobj.AiUsageReservation(id,user,date,old.reservedTokens(),old.runId(),expiry,old.modelStarted()));
        }
        public synchronized void markStarted(Long user,LocalDate date,String id){
            var old=reservations.get(id);if(old!=null)reservations.put(id,new online.yudream.base.domain.platform.ai.valobj.AiUsageReservation(id,user,date,old.reservedTokens(),old.runId(),old.leaseExpiresAt(),true));
        }
        public synchronized void uncertain(online.yudream.base.domain.platform.ai.valobj.AiUsageReservation r){
            uncertain(r,AiUsage.empty());
        }
        public synchronized void uncertain(online.yudream.base.domain.platform.ai.valobj.AiUsageReservation r,AiUsage known){
            var old=reservations.remove(r.id());if(old==null)return;
            var quota=quotas.get(r.userId()+"/"+r.usageDate());quota.setReservedTokens(quota.getReservedTokens()-old.reservedTokens());
            long held=Math.max(0,old.reservedTokens()-known.totalTokens());
            quota.setUsedTokens(quota.getUsedTokens()+known.totalTokens()); quota.setUncertainTokens(quota.getUncertainTokens()+held);
            uncertain.put(r.id(),new online.yudream.base.domain.platform.ai.valobj.AiUsageReservation(r.id(),r.userId(),r.usageDate(),held,r.runId(),null,true,known.totalTokens()));
        }
        public synchronized List<online.yudream.base.domain.platform.ai.valobj.AiUsageReservation> unresolved(Long user,LocalDate date){
            return uncertain.values().stream().filter(r->r.userId().equals(user)&&r.usageDate().equals(date)).toList();
        }
        void expire(String id){var old=reservations.get(id);renew(old.userId(),old.usageDate(),id,java.time.Instant.now().minusSeconds(1));}
    }
}
