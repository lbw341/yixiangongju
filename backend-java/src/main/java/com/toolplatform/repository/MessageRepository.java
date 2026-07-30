package com.toolplatform.repository;

import com.toolplatform.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByToUserIdOrFromUserIdOrderByCreatedAtDesc(Long toUserId, Long fromUserId);
    List<Message> findAllByOrderByCreatedAtDesc();
}
