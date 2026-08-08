package com.cloudstorage.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cloudstorage.model.entity.SecurityQuestion;

/**
 * SecurityQuestionRepository
 */
public interface SecurityQuestionRepository extends JpaRepository<SecurityQuestion, Long> {
    /**
     * 基于用户ID寻找密保问题
     * 
     * @param userId 用户ID
     * @return List SecurityQuestion
     */
    List<SecurityQuestion> findByUserId(Long userId);

    /**
     * 基于用户ID删除全部密保问题
     * 
     * @param userId 用户ID
     */
    void deleteByUserId(Long userId);

    void deleteByIdAndUserId(Long id, Long userId);

    /**
     * 更新用户密保问题
     * 
     * @param question 更新的问题
     * @param id       问题ID
     * @param userId   用户ID
     * @return int 1 成功 0 失败
     */
    @Modifying
    @Query("UPDATE SecurityQuestion s SET s.question = :question WHERE s.id = :id AND s.user.id = :userId")
    int updateQuestion(@Param("question") String question, @Param("id") Long id, @Param("userId") Long userId);

    /**
     * 更新用户密保问题的答案
     * 
     * @param answer 更新的答案
     * @param id     问题ID
     * @param userId 用户ID
     * @return int 1 成功 0 失败
     */
    @Modifying
    @Query("UPDATE SecurityQuestion s SET s.answer = :answer WHERE s.id = :id AND s.user.id = :userId")
    int updateAnswer(@Param("answer") String answer, @Param("id") Long id, @Param("userId") Long userId);
}
