package com.example.demo.constant;

public class JwtClaimsConstant {

    public static final String EMP_ID = "empId";

    // 双token: claims中标识令牌类型的key
    public static final String TOKEN_TYPE = "tokenType";
    // 访问令牌(短期), 用于访问业务接口
    public static final String ACCESS_TOKEN = "access";
    // 刷新令牌(长期), 仅用于调用刷新接口换取新的访问令牌
    public static final String REFRESH_TOKEN = "refresh";
}
