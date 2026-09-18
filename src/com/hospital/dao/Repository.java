package com.hospital.dao;

import com.hospital.exception.DataAccessException;

import java.util.List;
import java.util.Optional;

/**
 * Generic persistence contract (Unit 4: generics + collections).
 * The service layer only ever talks to this interface, which is why the
 * file-based and JDBC-based implementations are interchangeable.
 *
 * @param <T> entity type
 */
public interface Repository<T> {

    T save(T entity) throws DataAccessException;

    void update(T entity) throws DataAccessException;

    boolean deleteById(String id) throws DataAccessException;

    Optional<T> findById(String id) throws DataAccessException;

    List<T> findAll() throws DataAccessException;

    default long count() throws DataAccessException { return findAll().size(); }
}
