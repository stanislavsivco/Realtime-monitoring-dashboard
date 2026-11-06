package com.realtime_monitoring_dashboard.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.realtime_monitoring_dashboard.backend.dto.RegisterAgentRequestDTO;
import com.realtime_monitoring_dashboard.backend.dto.UserDTO;
import com.realtime_monitoring_dashboard.backend.exception.LastAdminException;
import com.realtime_monitoring_dashboard.backend.model.Role;
import com.realtime_monitoring_dashboard.backend.model.User;
import com.realtime_monitoring_dashboard.backend.repository.DeviceRepository;
import com.realtime_monitoring_dashboard.backend.repository.UserRepository;
import com.realtime_monitoring_dashboard.backend.support.PostgresTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({UserService.class, DeviceService.class})
class PostgresConcurrencyIntegrationTest {

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Autowired
    private UserService userService;
    @Autowired
    private DeviceService deviceService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(8);
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        cleanDatabase();
    }

    private void cleanDatabase() {
        userRepository.deleteAll();
        deviceRepository.deleteAll();
    }

    private User saveUser(String username, Role role) {
        return userRepository.save(User.builder().username(username).password("unused").role(role).build());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    void changingAdminRoleWaitsWhileAnotherTransactionHoldsTheAdminRowLocks() throws Exception {
        User first = saveUser("lock-admin-1", Role.ADMIN);
        saveUser("lock-admin-2", Role.ADMIN);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        Future<?> holder = executor.submit(() -> template.executeWithoutResult(status -> {
            userRepository.findAllByRoleForUpdate(Role.ADMIN);
            locked.countDown();
            await(release);
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        Future<UserDTO> demotion = executor.submit(() -> userService.changeUserRole(first.getId(), Role.VIEWER));

        assertThrows(TimeoutException.class, () -> demotion.get(1500, TimeUnit.MILLISECONDS));

        release.countDown();
        holder.get(10, TimeUnit.SECONDS);
        assertThat(demotion.get(10, TimeUnit.SECONDS).getRole()).isEqualTo(Role.VIEWER);
    }

    @Test
    void demotingBothAdminsAtTheSameTimeKeepsExactlyOneAdmin() throws Exception {
        User first = saveUser("race-admin-1", Role.ADMIN);
        User second = saveUser("race-admin-2", Role.ADMIN);
        CyclicBarrier start = new CyclicBarrier(2);

        List<Future<UserDTO>> results = List.of(first, second).stream()
                .map(user -> executor.submit((Callable<UserDTO>) () -> {
                    start.await(10, TimeUnit.SECONDS);
                    return userService.changeUserRole(user.getId(), Role.VIEWER);
                }))
                .toList();

        int succeeded = 0;
        int rejected = 0;
        for (Future<UserDTO> result : results) {
            try {
                result.get(30, TimeUnit.SECONDS);
                succeeded++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(LastAdminException.class);
                rejected++;
            }
        }

        assertThat(succeeded).isEqualTo(1);
        assertThat(rejected).isEqualTo(1);
        assertThat(userRepository.findAll()).filteredOn(user -> user.getRole() == Role.ADMIN).hasSize(1);
    }

    @Test
    void concurrentRegistrationOfTheSameHostnameCreatesOneDevice() throws Exception {
        int threads = 8;
        CyclicBarrier start = new CyclicBarrier(threads);

        List<Future<Long>> results = IntStream.range(0, threads)
                .mapToObj(i -> executor.submit((Callable<Long>) () -> {
                    start.await(10, TimeUnit.SECONDS);
                    return deviceService.registerAgent(new RegisterAgentRequestDTO("race-host", null, null)).getId();
                }))
                .toList();

        Set<Long> ids = new HashSet<>();
        for (Future<Long> result : results) {
            ids.add(result.get(30, TimeUnit.SECONDS));
        }

        assertThat(ids).hasSize(1);
        assertThat(deviceRepository.findAll()).hasSize(1);
    }
}