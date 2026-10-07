package com.example.demo.Vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 管理端用户分页结果
 * GET /api/admin/users
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserPageVO {
    /** 符合条件的总条数 */
    private Long total;
    /** 当前页码(从1开始) */
    private Integer page;
    /** 每页条数 */
    private Integer pageSize;
    /** 当前页列表 */
    private List<AdminUserListItemVO> list;
}
