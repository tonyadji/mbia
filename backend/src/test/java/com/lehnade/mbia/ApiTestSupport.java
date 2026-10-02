package com.lehnade.mbia;

import com.lehnade.mbia.family.FamilyFixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Whole application against PostgreSQL, called through MockMvc with tokens from {@link TestJwts}.
 * Every filter, validator and interceptor of production runs; only the key trusted by the
 * decoder differs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class ApiTestSupport {

    @TestBean(name = "jwtDecoder", methodName = "com.lehnade.mbia.TestJwts#decoder")
    JwtDecoder jwtDecoder;

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected JdbcClient jdbc;

    /** Families and members of each role, for family-scoped tests of any module. */
    protected FamilyFixtures families() {
        return new FamilyFixtures(mvc, jdbc);
    }

    protected long userRowsWithSubject(String subject) {
        return jdbc.sql("SELECT count(*) FROM users WHERE identity_provider_subject = ?")
                .param(subject)
                .query(Long.class)
                .single();
    }
}
