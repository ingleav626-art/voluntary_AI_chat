package com.voluntary.chat.server.service;

import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import com.voluntary.chat.server.dto.request.FriendApplyRequest;
import com.voluntary.chat.server.dto.response.FriendApplyResponse;
import com.voluntary.chat.server.dto.response.FriendResponse;
import com.voluntary.chat.server.entity.Friend;
import com.voluntary.chat.server.entity.FriendApply;
import com.voluntary.chat.server.entity.User;
import com.voluntary.chat.server.mapper.FriendApplyMapper;
import com.voluntary.chat.server.mapper.FriendMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * FriendService 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FriendService 单元测试")
class FriendServiceTest {

    @Mock
    private FriendApplyMapper friendApplyMapper;

    @Mock
    private FriendMapper friendMapper;

    @Mock
    private UserService userService;

    @InjectMocks
    private FriendService friendService;

    private User mockTargetUser;
    private User mockApplicant;

    private static final String TARGET_PHONE = "13800138002";
    private static final String APPLICANT_PHONE = "13800138001";

    @BeforeEach
    void setUp() {
        mockTargetUser = new User();
        mockTargetUser.setId(1002L);
        mockTargetUser.setUsername("李四");
        mockTargetUser.setAvatar("http://example.com/avatar2.jpg");
        mockTargetUser.setPhone(TARGET_PHONE);

        mockApplicant = new User();
        mockApplicant.setId(1001L);
        mockApplicant.setUsername("张三");
        mockApplicant.setAvatar("http://example.com/avatar1.jpg");
        mockApplicant.setPhone(APPLICANT_PHONE);
    }

    @Test
    @DisplayName("发送好友申请成功")
    void applyFriend_shouldSucceed() {
        FriendApplyRequest request = new FriendApplyRequest();
        request.setTargetPhone(TARGET_PHONE);
        request.setMessage("你好，认识一下");

        when(userService.findByPhone(TARGET_PHONE)).thenReturn(mockTargetUser);
        when(friendMapper.selectCount(any())).thenReturn(0L);
        when(friendApplyMapper.selectOne(any())).thenReturn(null);

        friendService.applyFriend(1001L, request);

        verify(friendApplyMapper).insert(any(FriendApply.class));
    }

    @Test
    @DisplayName("目标用户不存在时抛出异常")
    void applyFriend_shouldThrow_whenUserNotFound() {
        FriendApplyRequest request = new FriendApplyRequest();
        request.setTargetPhone("13900000000");

        when(userService.findByPhone("13900000000")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> friendService.applyFriend(1001L, request));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("不能添加自己为好友")
    void applyFriend_shouldThrow_whenAddSelf() {
        FriendApplyRequest request = new FriendApplyRequest();
        request.setTargetPhone(APPLICANT_PHONE);

        when(userService.findByPhone(APPLICANT_PHONE)).thenReturn(mockApplicant);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> friendService.applyFriend(1001L, request));
        assertEquals(ErrorCode.CANNOT_ADD_SELF, ex.getErrorCode());
    }

    @Test
    @DisplayName("已是好友关系时抛出异常")
    void applyFriend_shouldThrow_whenAlreadyFriends() {
        FriendApplyRequest request = new FriendApplyRequest();
        request.setTargetPhone(TARGET_PHONE);

        when(userService.findByPhone(TARGET_PHONE)).thenReturn(mockTargetUser);
        when(friendMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> friendService.applyFriend(1001L, request));
        assertEquals(ErrorCode.ALREADY_FRIENDS, ex.getErrorCode());
    }

    @Test
    @DisplayName("已存在待处理申请时抛出异常")
    void applyFriend_shouldThrow_whenApplyExists() {
        FriendApplyRequest request = new FriendApplyRequest();
        request.setTargetPhone(TARGET_PHONE);

        FriendApply existing = new FriendApply();
        existing.setId(5001L);

        when(userService.findByPhone(TARGET_PHONE)).thenReturn(mockTargetUser);
        when(friendMapper.selectCount(any())).thenReturn(0L);
        // 第一次 selectOne 检查反向申请返回 null，第二次检查正向申请返回已存在记录
        when(friendApplyMapper.selectOne(any())).thenReturn(null, existing);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> friendService.applyFriend(1001L, request));
        assertEquals(ErrorCode.FRIEND_APPLY_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("双向申请自动建立好友关系")
    void applyFriend_shouldAutoAccept_whenReverseApplyExists() {
        FriendApplyRequest request = new FriendApplyRequest();
        request.setTargetPhone(TARGET_PHONE);

        FriendApply reverseApply = new FriendApply();
        reverseApply.setId(5001L);
        reverseApply.setUserId(1002L);
        reverseApply.setTargetUserId(1001L);
        reverseApply.setStatus(0);

        when(userService.findByPhone(TARGET_PHONE)).thenReturn(mockTargetUser);
        when(friendMapper.selectCount(any())).thenReturn(0L);
        when(friendApplyMapper.selectOne(any())).thenReturn(reverseApply);

        friendService.applyFriend(1001L, request);

        verify(friendApplyMapper).updateById(reverseApply);
        verify(friendMapper, times(2)).insert(any(Friend.class));
    }

    @Test
    @DisplayName("获取好友申请列表成功")
    void getApplyList_shouldSucceed() {
        FriendApply apply = new FriendApply();
        apply.setId(5001L);
        apply.setUserId(1001L);
        apply.setTargetUserId(1002L);
        apply.setMessage("你好");
        apply.setStatus(0);
        apply.setCreateTime(LocalDateTime.now());

        when(friendApplyMapper.selectList(any())).thenReturn(List.of(apply));
        when(userService.findByIds(Set.of(1001L))).thenReturn(Map.of(1001L, mockApplicant));

        List<FriendApplyResponse> result = friendService.getApplyList(1002L);

        assertEquals(1, result.size());
        FriendApplyResponse resp = result.get(0);
        assertEquals(5001L, resp.getApplyId());
        assertEquals(1001L, resp.getUserId());
        assertEquals("张三", resp.getUsername());
        assertEquals("你好", resp.getMessage());
        assertEquals("PENDING", resp.getStatus());
    }

    @Test
    @DisplayName("获取好友申请列表-空列表")
    void getApplyList_shouldReturnEmpty_whenNoApplies() {
        when(friendApplyMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<FriendApplyResponse> result = friendService.getApplyList(1002L);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("处理好友申请-同意")
    void handleApply_shouldAccept() {
        FriendApply apply = new FriendApply();
        apply.setId(5001L);
        apply.setUserId(1001L);
        apply.setTargetUserId(1002L);
        apply.setStatus(0);

        when(friendApplyMapper.selectById(5001L)).thenReturn(apply);
        when(friendMapper.selectCount(any())).thenReturn(0L);

        friendService.handleApply(1002L, 5001L, "ACCEPT");

        verify(friendApplyMapper).updateById(apply);
        verify(friendMapper, times(2)).insert(any(Friend.class));
    }

    @Test
    @DisplayName("处理好友申请-拒绝")
    void handleApply_shouldReject() {
        FriendApply apply = new FriendApply();
        apply.setId(5001L);
        apply.setUserId(1001L);
        apply.setTargetUserId(1002L);
        apply.setStatus(0);

        when(friendApplyMapper.selectById(5001L)).thenReturn(apply);

        friendService.handleApply(1002L, 5001L, "REJECT");

        verify(friendApplyMapper).updateById(apply);
        verify(friendMapper, never()).insert(any(Friend.class));
    }

    @Test
    @DisplayName("处理好友申请-申请不存在")
    void handleApply_shouldThrow_whenNotFound() {
        when(friendApplyMapper.selectById(5001L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> friendService.handleApply(1002L, 5001L, "ACCEPT"));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("处理好友申请-无权限")
    void handleApply_shouldThrow_whenNoPermission() {
        FriendApply apply = new FriendApply();
        apply.setId(5001L);
        apply.setUserId(1001L);
        apply.setTargetUserId(1003L);
        apply.setStatus(0);

        when(friendApplyMapper.selectById(5001L)).thenReturn(apply);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> friendService.handleApply(1002L, 5001L, "ACCEPT"));
        assertEquals(ErrorCode.NO_PERMISSION, ex.getErrorCode());
    }

    @Test
    @DisplayName("处理好友申请-已处理")
    void handleApply_shouldThrow_whenAlreadyHandled() {
        FriendApply apply = new FriendApply();
        apply.setId(5001L);
        apply.setUserId(1001L);
        apply.setTargetUserId(1002L);
        apply.setStatus(1);

        when(friendApplyMapper.selectById(5001L)).thenReturn(apply);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> friendService.handleApply(1002L, 5001L, "ACCEPT"));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("处理好友申请-无效动作")
    void handleApply_shouldThrow_whenInvalidAction() {
        FriendApply apply = new FriendApply();
        apply.setId(5001L);
        apply.setUserId(1001L);
        apply.setTargetUserId(1002L);
        apply.setStatus(0);

        when(friendApplyMapper.selectById(5001L)).thenReturn(apply);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> friendService.handleApply(1002L, 5001L, "INVALID"));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("获取好友列表成功")
    void getFriendList_shouldSucceed() {
        Friend friend = new Friend();
        friend.setId(6001L);
        friend.setUserId(1001L);
        friend.setFriendId(1002L);
        friend.setRemark("四哥");

        when(friendMapper.selectList(any())).thenReturn(List.of(friend));
        when(userService.findByIds(Set.of(1002L))).thenReturn(Map.of(1002L, mockTargetUser));

        List<FriendResponse> result = friendService.getFriendList(1001L);

        assertEquals(1, result.size());
        FriendResponse resp = result.get(0);
        assertEquals(1002L, resp.getUserId());
        assertEquals("李四", resp.getUsername());
        assertEquals("四哥", resp.getRemark());
    }

    @Test
    @DisplayName("获取好友列表-空列表")
    void getFriendList_shouldReturnEmpty_whenNoFriends() {
        when(friendMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<FriendResponse> result = friendService.getFriendList(1001L);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("删除好友成功")
    void deleteFriend_shouldSucceed() {
        when(friendMapper.selectCount(any())).thenReturn(1L);

        friendService.deleteFriend(1001L, 1002L);

        verify(friendMapper, times(2)).delete(any());
    }

    @Test
    @DisplayName("删除好友-好友关系不存在")
    void deleteFriend_shouldThrow_whenNotFriend() {
        when(friendMapper.selectCount(any())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> friendService.deleteFriend(1001L, 1002L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("判断是否为好友-是好友")
    void isFriend_shouldReturnTrue_whenFriend() {
        when(friendMapper.selectCount(any())).thenReturn(1L);

        assertTrue(friendService.isFriend(1001L, 1002L));
    }

    @Test
    @DisplayName("判断是否为好友-非好友")
    void isFriend_shouldReturnFalse_whenNotFriend() {
        when(friendMapper.selectCount(any())).thenReturn(0L);

        assertFalse(friendService.isFriend(1001L, 1002L));
    }
}
