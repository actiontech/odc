/*
 * Copyright (c) 2023 OceanBase.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.oceanbase.odc.config.jpa;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.IntStream;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.sql.DataSource;

import org.hibernate.engine.jdbc.connections.internal.DatasourceConnectionProviderImpl;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.internal.SessionFactoryImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.google.common.base.Preconditions;
import com.oceanbase.odc.common.util.JdbcOperationsUtil;

import lombok.SneakyThrows;

public class EnhancedJpaRepository<T, ID extends Serializable> extends SimpleJpaRepository<T, ID> {

    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private EntityManager entityManager;

    private JpaEntityInformation<T, ?> entityInformation;

    public EnhancedJpaRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityInformation = entityInformation;
        this.entityManager = entityManager;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(getDataSource(entityManager));
    }

    public JdbcTemplate getJdbcTemplate() {
        return (JdbcTemplate) namedParameterJdbcTemplate.getJdbcOperations();
    }

    public NamedParameterJdbcTemplate getNamedParameterJdbcTemplate() {
        return namedParameterJdbcTemplate;
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    @SneakyThrows
    @Transactional
    public List<T> batchCreate(List<T> entities, String sql, Map<Integer, Function<T, Object>> valueGetter,
            BiConsumer<T, Long> idSetter) {
        Preconditions.checkArgument(entities.stream().allMatch(e -> entityInformation.getId(e) == null),
                "can't create entity, cause not new entities");
        return JdbcOperationsUtil.batchCreate(getJdbcTemplate(), entities, sql, valueGetter, idSetter);
    }

    @SneakyThrows
    @Transactional
    public List<T> batchCreate(List<T> entities, String sql, List<Function<T, Object>> valueGetter,
            BiConsumer<T, Long> idSetter) {
        Map<Integer, Function<T, Object>> valueGetterMap = new HashMap<>();
        IntStream.range(1, valueGetter.size() + 1).forEach(i -> valueGetterMap.put(i, valueGetter.get(i - 1)));
        return batchCreate(entities, sql, valueGetterMap, idSetter);
    }

    @SneakyThrows
    @Transactional
    public List<T> batchCreate(List<T> entities, String sql, Map<Integer, Function<T, Object>> valueGetter,
            BiConsumer<T, Long> idSetter, int batchSize) {
        Preconditions.checkArgument(entities.stream().allMatch(e -> entityInformation.getId(e) == null),
                "can't create entity, cause not new entities");
        return JdbcOperationsUtil.batchCreate(getJdbcTemplate(), entities, sql, valueGetter, idSetter, batchSize);
    }

    @SneakyThrows
    @Transactional
    public List<T> batchCreate(List<T> entities, String sql, List<Function<T, Object>> valueGetter,
            BiConsumer<T, Long> idSetter, int batchSize) {
        Map<Integer, Function<T, Object>> valueGetterMap = new HashMap<>();
        IntStream.range(1, valueGetter.size() + 1).forEach(i -> valueGetterMap.put(i, valueGetter.get(i - 1)));
        return batchCreate(entities, sql, valueGetterMap, idSetter, batchSize);
    }

    @Override
    protected <S extends T> TypedQuery<Long> getCountQuery(Specification<S> spec, Class<S> domainClass) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);

        Root<S> root = applySpecificationToCriteria(spec, domainClass, query);

        /**
         * if group by, we calculate the count of the group instead of the sum of all group items<br/>
         * this is the only difference with SimpleJpaRepository#getCountQuery
         */
        if (query.isDistinct() || !query.getGroupList().isEmpty()) {
            query.select(builder.countDistinct(root));
        } else {
            query.select(builder.count(root));
        }

        // Remove all Orders the Specifications might have applied
        query.orderBy(Collections.emptyList());

        return entityManager.createQuery(query);
    }

    private DataSource getDataSource(EntityManager entityManager) {
        SessionFactoryImpl sf = entityManager.getEntityManagerFactory().unwrap(SessionFactoryImpl.class);
        return ((DatasourceConnectionProviderImpl) sf.getServiceRegistry().getService(ConnectionProvider.class))
                .getDataSource();
    }

    /**
     * 从ResultSet中获取生成的主键ID
     *
     * 问题描述：
     * 
     * - MySQL批量插入时，getGeneratedKeys()返回的ResultSet可能没有列名，导致通过列名访问（如getObject("id")）抛出SQLException:
     * Column 'id' not found - 不同数据库驱动对getGeneratedKeys()返回的ResultSet列名处理不一致： - MySQL:
     * 批量插入时可能无列名，或列名为"GENERATED_KEY" - Oracle: 可能有实际列名或"GENERATED_KEY" - SQL Server:
     * 列名可能为"GENERATED_KEYS"或实际列名 - OceanBase for MySQL: 兼容MySQL协议，行为与MySQL相同
     *
     * 解决方案： -
     * 优先通过索引访问（resultSet.getObject(1)）：JDBC规范强制要求getGeneratedKeys()返回的ResultSet第一列就是生成的主键，这是标准做法，不依赖列名，适用于所有数据库
     * - 回退到列名访问：如果索引访问失败（理论上不应该），尝试通过常见列名访问，兼容不同驱动的列名差异
     *
     * 兼容性保证： - 索引访问（第1列）：100%兼容所有数据库，符合JDBC规范 - 列名回退机制：处理特殊情况，提供额外容错保障 -
     * 异常容错：多层try-catch确保不会因列名问题导致程序崩溃
     *
     * @param resultSet getGeneratedKeys()返回的ResultSet，已调用next()定位到当前行
     * @return 生成的主键ID，如果无法获取则返回null
     * @throws SQLException 如果发生数据库访问错误
     */
    private Long getGeneratedId(ResultSet resultSet) throws SQLException {
        // JDBC规范：getGeneratedKeys()返回的ResultSet第一列就是生成的主键
        // 优先通过索引访问（兼容MySQL批量插入时无列名的情况）
        try {
            Object value = resultSet.getObject(1);
            if (value != null) {
                return Long.valueOf(value.toString());
            }
        } catch (SQLException e) {
            // 如果索引访问失败，继续尝试通过列名访问
        }

        // 兼容不同数据库驱动的列名差异
        String[] columnNames = {"id", "ID", "GENERATED_KEY"};
        for (String columnName : columnNames) {
            try {
                Object value = resultSet.getObject(columnName);
                if (value != null) {
                    return Long.valueOf(value.toString());
                }
            } catch (SQLException e) {
                // 继续尝试下一个列名
            }
        }

        return null;
    }


    private <S, U extends T> Root<U> applySpecificationToCriteria(@Nullable Specification<U> spec, Class<U> domainClass,
            CriteriaQuery<S> query) {

        Assert.notNull(domainClass, "Domain class must not be null!");
        Assert.notNull(query, "CriteriaQuery must not be null!");

        Root<U> root = query.from(domainClass);

        if (spec == null) {
            return root;
        }

        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        Predicate predicate = spec.toPredicate(root, query, builder);

        if (predicate != null) {
            query.where(predicate);
        }

        return root;
    }

}
