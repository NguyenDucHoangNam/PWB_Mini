package com.pwb.iam.testsupport;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.pwb.iam.infrastructure.config.IamJpaConfig;
import com.pwb.iam.infrastructure.persistence.adapter.RoleRepositoryImpl;
import com.pwb.iam.infrastructure.persistence.mapper.AuditLogMapper;
import com.pwb.iam.infrastructure.persistence.mapper.OtpCodeMapper;
import com.pwb.iam.infrastructure.persistence.mapper.PasswordHistoryMapper;
import com.pwb.iam.infrastructure.persistence.mapper.PasswordResetTokenMapper;
import com.pwb.iam.infrastructure.persistence.mapper.RoleMapper;
import com.pwb.iam.infrastructure.persistence.mapper.UserMapper;

@SpringBootTest(classes = TestIamConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({
        IamJpaConfig.class,
        RoleRepositoryImpl.class,
        UserMapper.class,
        RoleMapper.class,
        OtpCodeMapper.class,
        PasswordHistoryMapper.class,
        PasswordResetTokenMapper.class,
        AuditLogMapper.class
})
public abstract class AbstractRepositoryIT {
}