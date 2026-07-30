package com.toolplatform.service;

import com.toolplatform.entity.Message;
import com.toolplatform.entity.User;
import com.toolplatform.repository.MessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 消息服务层
 * 处理消息的创建、回复、已读等业务逻辑
 */
@Service
public class MessageService {

    private final MessageRepository msgRepo;

    public MessageService(MessageRepository msgRepo) {
        this.msgRepo = msgRepo;
    }

    /**
     * 获取用户消息列表
     */
    public List<Message> listMessages(User user) {
        if ("admin".equals(user.getRole())) {
            return msgRepo.findAllByOrderByCreatedAtDesc();
        } else {
            return msgRepo.findByToUserIdOrFromUserIdOrderByCreatedAtDesc(user.getId(), user.getId());
        }
    }

    /**
     * 创建消息
     */
    public Message createMessage(User user, Map<String, Object> body) {
        String title = (String) body.getOrDefault("title", "");
        if (title.trim().isEmpty()) {
            throw new IllegalArgumentException("标题不能为空");
        }

        Message msg = new Message();
        msg.setType((String) body.getOrDefault("type", "问题反馈"));
        msg.setTitle(title);
        msg.setContent((String) body.getOrDefault("content", ""));
        msg.setFromUser(user.getNickname() != null && !user.getNickname().isEmpty() ? user.getNickname() : user.getUsername());
        msg.setFromUserId(user.getId());

        if (body.containsKey("to_user_id") && body.get("to_user_id") != null) {
            msg.setToUserId(Long.valueOf(body.get("to_user_id").toString()));
        }
        if (body.containsKey("tool_id") && body.get("tool_id") != null) {
            msg.setToolId(Long.valueOf(body.get("tool_id").toString()));
        }
        msg.setStatus("未读");
        return msgRepo.save(msg);
    }

    /**
     * 回复消息
     */
    public Message replyMessage(Long id, String reply) {
        Message msg = msgRepo.findById(id).orElse(null);
        if (msg == null) throw new IllegalArgumentException("消息不存在");
        if (reply == null || reply.trim().isEmpty()) throw new IllegalArgumentException("回复内容不能为空");

        msg.setReplyContent(reply.trim());
        msg.setStatus("已答复");
        return msgRepo.save(msg);
    }

    /**
     * 标记消息为已读
     */
    public void markAsRead(Long id) {
        msgRepo.findById(id).ifPresent(msg -> {
            msg.setStatus("已读");
            msgRepo.save(msg);
        });
    }
}
