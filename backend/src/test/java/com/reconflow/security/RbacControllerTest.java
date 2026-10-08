package com.reconflow.security;

import com.reconflow.entity.ExceptionQueue;
import com.reconflow.repository.ExceptionQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.reconflow.entity.ReconciliationRun;
import com.reconflow.repository.ReconciliationRunRepository;
import java.time.ZonedDateTime;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RbacControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ExceptionQueueRepository exceptionQueueRepository;

    @Autowired
    private ReconciliationRunRepository reconciliationRunRepository;

    @Autowired
    private com.reconflow.repository.UserRepository userRepository;

    private String analystToken;
    private String managerToken;
    private String adminToken;
    
    private UUID analystId;
    private UUID managerId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        exceptionQueueRepository.deleteAll();
        reconciliationRunRepository.deleteAll();
        userRepository.deleteAll();
        
        com.reconflow.entity.User savedAnalyst = userRepository.save(com.reconflow.entity.User.builder().name("Analyst").username("analyst@reconflow.com").password("pass").role("ROLE_ANALYST").build());
        analystId = savedAnalyst.getId();
        
        com.reconflow.entity.User savedManager = userRepository.save(com.reconflow.entity.User.builder().name("Manager").username("manager@reconflow.com").password("pass").role("ROLE_MANAGER").build());
        managerId = savedManager.getId();
        
        com.reconflow.entity.User savedAdmin = userRepository.save(com.reconflow.entity.User.builder().name("Admin").username("admin@reconflow.com").password("pass").role("ROLE_ADMIN").build());
        adminId = savedAdmin.getId();
        
        analystToken = jwtService.generateToken("analyst@reconflow.com", "ROLE_ANALYST", analystId.toString());
        managerToken = jwtService.generateToken("manager@reconflow.com", "ROLE_MANAGER", managerId.toString());
        adminToken = jwtService.generateToken("admin@reconflow.com", "ROLE_ADMIN", adminId.toString());

        ReconciliationRun run = ReconciliationRun.builder()
                .runDate(java.time.LocalDate.now())
                .status("COMPLETED")
                .startedAt(ZonedDateTime.now())
                .completedAt(ZonedDateTime.now())
                .chainATotal(10)
                .chainAMatched(8)
                .chainAExceptions(2)
                .build();
        run = reconciliationRunRepository.save(run);

        // Seed some exceptions assigned to Analyst
        ExceptionQueue analystException = ExceptionQueue.builder()
                .reconciliationRunId(run.getId())
                .matchId(UUID.randomUUID())
                .matchType("A")
                .exceptionType("AMOUNT_MISMATCH")
                .discrepancyAmount(new BigDecimal("10.00"))
                .currency("INR")
                .status("OPEN")
                .assignedTo(analystId)
                .build();
                
        // Seed some exceptions assigned to Manager
        ExceptionQueue managerException = ExceptionQueue.builder()
                .reconciliationRunId(run.getId())
                .matchId(UUID.randomUUID())
                .matchType("A")
                .exceptionType("MISSING_IN_LEDGER")
                .discrepancyAmount(new BigDecimal("50.00"))
                .currency("INR")
                .status("OPEN")
                .assignedTo(managerId)
                .build();
                
        exceptionQueueRepository.save(analystException);
        exceptionQueueRepository.save(managerException);
    }

    @Test
    void testRoleEscalationAnalystToManagerEndpoint() throws Exception {
        // Upload is restricted to MANAGER and above
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "col1,col2\nval1,val2".getBytes());

        // Analyst attempting to upload should get 403 Forbidden
        mockMvc.perform(multipart("/api/v1/upload")
                .file(file)
                .param("sourceType", "LEDGER")
                .param("settlementDate", "2026-10-07")
                .cookie(new Cookie("accessToken", analystToken)))
                .andExpect(status().isForbidden());
    }
    
    @Test
    void testManagerAccessToManagerEndpoint() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "internal_order_id,gateway_transaction_id,amount,currency,gateway,transaction_date\nORD-MGR-1,REF-1,100,INR,RZP,2026-10-07".getBytes());

        // Manager attempting to upload should get 201 Created (Or 400 Bad Request if file parsing fails, but NOT 403)
        mockMvc.perform(multipart("/api/v1/upload")
                .file(file)
                .param("sourceType", "LEDGER")
                .param("settlementDate", "2026-10-07")
                .cookie(new Cookie("accessToken", managerToken)))
                .andExpect(status().isCreated());
    }

    @Test
    void testAdminAccessToManagerEndpoint() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "internal_order_id,gateway_transaction_id,amount,currency,gateway,transaction_date\nORD-ADM-1,REF-1,100,INR,RZP,2026-10-07".getBytes());

        // Admin attempting to upload should also succeed (Role Hierarchy)
        mockMvc.perform(multipart("/api/v1/upload")
                .file(file)
                .param("sourceType", "LEDGER")
                .param("settlementDate", "2026-10-07")
                .cookie(new Cookie("accessToken", adminToken)))
                .andExpect(status().isCreated());
    }
    
    @Test
    void testDataScopingAnalystSeesOnlyAssigned() throws Exception {
        // Analyst should only see 1 exception
        mockMvc.perform(get("/api/v1/exceptions")
                .cookie(new Cookie("accessToken", analystToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].exceptionType").value("AMOUNT_MISMATCH"));
    }
    
    @Test
    void testDataScopingManagerSeesAll() throws Exception {
        // Manager should see both exceptions
        mockMvc.perform(get("/api/v1/exceptions")
                .cookie(new Cookie("accessToken", managerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
    
    @Test
    void testRoleSwitchAdminToAnalystViaBypassAttempt() throws Exception {
        // Attempt to switch role from ADMIN to ANALYST without re-authenticating (bypass/crack attempt)
        // 1. Take the valid Admin token
        String[] chunks = adminToken.split("\\.");
        
        // 2. Decode the payload, change ROLE_ADMIN to ROLE_ANALYST, re-encode (Base64)
        String originalPayload = new String(java.util.Base64.getUrlDecoder().decode(chunks[1]));
        String hackedPayload = originalPayload.replace("ROLE_ADMIN", "ROLE_ANALYST");
        String encodedHackedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hackedPayload.getBytes());
        
        // 3. Reconstruct token with original signature (which should now be invalid for this payload)
        String hackedToken = chunks[0] + "." + encodedHackedPayload + "." + chunks[2];
        
        // 4. Attempt to hit the GET exceptions endpoint (Data Scoping test)
        // If the hack succeeded, the server would treat us as Analyst and only return 1 exception.
        // Since the signature is invalid, it should reject us entirely.
        mockMvc.perform(get("/api/v1/exceptions")
                .cookie(new Cookie("accessToken", hackedToken)))
                .andExpect(status().isForbidden());
    }
}
