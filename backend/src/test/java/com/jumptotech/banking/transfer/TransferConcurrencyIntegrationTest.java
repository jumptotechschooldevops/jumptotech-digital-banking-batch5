package com.jumptotech.banking.transfer;

import com.jumptotech.banking.AbstractIntegrationTest;
import com.jumptotech.banking.account.Account;
import com.jumptotech.banking.account.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TransferConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransferRepository transferRepository;

    private void creditDirectly(UUID accountId, long cents) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        account.credit(cents);
        accountRepository.save(account);
    }

    @Test
    void concurrentRequestsWithTheSameIdempotencyKeyExecuteTheTransferExactlyOnce() throws Exception {
        Session session = newSession();
        List<UUID> accountIds = registerLoginAndGetAccountIds(session, uniqueEmail("race-same-key"));
        UUID checking = accountIds.get(0);
        UUID savings = accountIds.get(1);
        creditDirectly(checking, 100_000L);

        Map<String, String> cookies = session.cookieSnapshot();
        String sharedIdempotencyKey = UUID.randomUUID().toString();
        Map<String, Object> body = Map.of("sourceAccountId", checking, "destinationAccountId", savings, "amountCents", 5_000);

        int concurrency = 15;
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CyclicBarrier barrier = new CyclicBarrier(concurrency);
        try {
            List<Future<ResponseEntity<String>>> futures = IntStream.range(0, concurrency)
                    .mapToObj(i -> pool.submit(() -> {
                        barrier.await();
                        return postWithFixedCookies(cookies, "/api/v1/transfers", body, sharedIdempotencyKey);
                    }))
                    .toList();

            List<ResponseEntity<String>> responses = new java.util.ArrayList<>();
            for (Future<ResponseEntity<String>> f : futures) {
                responses.add(f.get(30, TimeUnit.SECONDS));
            }

            long created = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count();
            long replayed = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.OK).count();
            assertThat(created).isEqualTo(1);
            assertThat(replayed).isEqualTo(concurrency - 1L);

            java.util.Set<String> transferIds = responses.stream()
                    .map(this::json)
                    .map(n -> n.get("transferId").asText())
                    .collect(Collectors.toSet());
            assertThat(transferIds).hasSize(1); // every response refers to the same transfer

            Account checkingAfter = accountRepository.findById(checking).orElseThrow();
            assertThat(checkingAfter.getBalanceCents()).isEqualTo(95_000L); // debited exactly once
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentOppositeDirectionTransfersDoNotDeadlockAndConserveTotalMoney() throws Exception {
        Session aSession = newSession();
        List<UUID> aAccounts = registerLoginAndGetAccountIds(aSession, uniqueEmail("race-a"));
        Session bSession = newSession();
        List<UUID> bAccounts = registerLoginAndGetAccountIds(bSession, uniqueEmail("race-b"));

        UUID accountA = aAccounts.get(0);
        UUID accountB = bAccounts.get(0);
        creditDirectly(accountA, 1_000_000L); // $10,000.00
        creditDirectly(accountB, 1_000_000L);

        long totalBefore = accountRepository.findById(accountA).orElseThrow().getBalanceCents()
                + accountRepository.findById(accountB).orElseThrow().getBalanceCents();

        Map<String, String> cookiesA = aSession.cookieSnapshot();
        Map<String, String> cookiesB = bSession.cookieSnapshot();

        int rounds = 40;
        ExecutorService pool = Executors.newFixedThreadPool(20);
        AtomicInteger aToBSuccesses = new AtomicInteger();
        AtomicInteger bToASuccesses = new AtomicInteger();
        java.util.concurrent.ConcurrentLinkedQueue<String> anomalies = new java.util.concurrent.ConcurrentLinkedQueue<>();
        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < rounds; i++) {
                String keyAB = UUID.randomUUID().toString();
                String keyBA = UUID.randomUUID().toString();
                futures.add(pool.submit(() -> {
                    ResponseEntity<String> r = postWithFixedCookies(cookiesA, "/api/v1/transfers",
                            Map.of("sourceAccountId", accountA, "destinationAccountId", accountB, "amountCents", 100), keyAB);
                    if (r.getStatusCode() == HttpStatus.CREATED) {
                        aToBSuccesses.incrementAndGet();
                    } else {
                        anomalies.add("A->B key=" + keyAB + " status=" + r.getStatusCode() + " body=" + r.getBody());
                    }
                }));
                futures.add(pool.submit(() -> {
                    ResponseEntity<String> r = postWithFixedCookies(cookiesB, "/api/v1/transfers",
                            Map.of("sourceAccountId", accountB, "destinationAccountId", accountA, "amountCents", 100), keyBA);
                    if (r.getStatusCode() == HttpStatus.CREATED) {
                        bToASuccesses.incrementAndGet();
                    } else {
                        anomalies.add("B->A key=" + keyBA + " status=" + r.getStatusCode() + " body=" + r.getBody());
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS); // would time out if the opposite-direction lock order caused a deadlock
            }
        } finally {
            pool.shutdownNow();
        }
        if (!anomalies.isEmpty()) {
            System.out.println("=== Non-CREATED responses (" + anomalies.size() + ") ===");
            anomalies.forEach(System.out::println);
        }

        long balanceA = accountRepository.findById(accountA).orElseThrow().getBalanceCents();
        long balanceB = accountRepository.findById(accountB).orElseThrow().getBalanceCents();

        assertThat(balanceA + balanceB).isEqualTo(totalBefore); // no money created or destroyed
        long expectedA = 1_000_000L - (aToBSuccesses.get() * 100L) + (bToASuccesses.get() * 100L);
        assertThat(balanceA).isEqualTo(expectedA); // arithmetic matches exactly what actually succeeded - no lost updates
    }
}
