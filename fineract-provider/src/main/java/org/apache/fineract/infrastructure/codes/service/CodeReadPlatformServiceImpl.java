/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.codes.service;

import static org.apache.fineract.query.QMCode.mCode;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Predicate;
import com.querydsl.sql.SQLQueryFactory;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import org.apache.fineract.infrastructure.codes.data.CodeData;
import org.apache.fineract.infrastructure.codes.exception.CodeNotFoundException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CodeReadPlatformServiceImpl implements CodeReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;
    private final SQLQueryFactory queryFactory;

    @Autowired
    public CodeReadPlatformServiceImpl(final PlatformSecurityContext context, final JdbcTemplate jdbcTemplate,
            final SQLQueryFactory queryFactory) {
        this.context = context;
        this.jdbcTemplate = jdbcTemplate;
        this.queryFactory = queryFactory;
    }

    private static final class CodeMapper implements RowMapper<CodeData> {

        public String schema() {
            return " c.id as id, c.code_name as code_name, c.is_system_defined as systemDefined from m_code c ";
        }

        @Override
        public CodeData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {

            final Long id = rs.getLong("id");
            final String code_name = rs.getString("code_name");
            final boolean systemDefined = rs.getBoolean("systemDefined");

            return CodeData.instance(id, code_name, systemDefined);
        }
    }

    @Override
    @Cacheable(value = "codes", key = "T(org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil).getTenant().getTenantIdentifier().concat('CD')")
    public Collection<CodeData> retrieveAllCodes() {
        this.context.authenticatedUser();

        final CodeMapper rm = new CodeMapper();
        final String sql = "select " + rm.schema() + " order by c.code_name";

        return this.jdbcTemplate.query(sql, rm); // NOSONAR
    }

    @Override
    public CodeData retrieveCode(final Long codeId) {
        this.context.authenticatedUser();

        // m_code.id is an INT column, so an id outside the int range cannot match any row
        if (codeId == null || codeId < Integer.MIN_VALUE || codeId > Integer.MAX_VALUE) {
            throw new CodeNotFoundException(codeId);
        }
        final CodeData code = retrieveOneCode(mCode.id.eq(codeId.intValue()));
        if (code == null) {
            throw new CodeNotFoundException(codeId);
        }
        return code;
    }

    @Override
    public CodeData retrieveCode(final String codeName) {
        this.context.authenticatedUser();

        if (codeName == null) {
            throw new CodeNotFoundException(codeName);
        }
        final CodeData code = retrieveOneCode(mCode.codeName.eq(codeName));
        if (code == null) {
            throw new CodeNotFoundException(codeName);
        }
        return code;
    }

    private CodeData retrieveOneCode(final Predicate predicate) {
        final Tuple row = queryFactory.select(mCode.id, mCode.codeName, mCode.isSystemDefined).from(mCode).where(predicate).fetchOne();
        if (row == null) {
            return null;
        }
        return CodeData.instance(row.get(mCode.id).longValue(), row.get(mCode.codeName),
                Boolean.TRUE.equals(row.get(mCode.isSystemDefined)));
    }
}
