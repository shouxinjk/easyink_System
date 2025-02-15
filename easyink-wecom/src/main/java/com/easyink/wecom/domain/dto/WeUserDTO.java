package com.easyink.wecom.domain.dto;

import com.easyink.common.core.domain.wecom.WeUser;
import com.easyink.common.utils.bean.BeanUtils;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;

/**
 * @description: 通讯录用户
 * @author admin
 * @create: 2020-08-28 10:45
 **/
@Data
@NoArgsConstructor
public class WeUserDTO extends WeResultDTO {

    //成员头像的mediaid，通过素材管理接口上传图片获得的mediaid
    private String avatar_mediaid;

    private String avatar;

    //姓名
    private String name;
    //昵称
    private String alias;
    //账号
    private String userid;

    //性别。1表示男性，2表示女性
    private Integer gender;

    //手机号码。企业内必须唯一，mobile/email二者不能同时为空
    private String mobile;

    //邮箱
    private String email;

    //成员所属部门id列表,不超过100个
    private String[] department;

    //职位
    private String position;

    //身份:表示在所在的部门内是否为上级。1表示为上级，0表示非上级。
    private String[] is_leader_in_dept;

    //启用/禁用成员。1表示启用成员，0表示禁用成员
    private Integer enable;

    //座机。32字节以内，由纯数字或’-‘号组成。
    private String telephone;

    //地址
    private String address;

    //全局唯一。对于同一个服务商，不同应用获取到企业内同一个成员的open_userid是相同的，最多64个字节。仅第三方应用可获取
    private String open_userid;

    //激活状态: 1=已激活，2=已禁用，4=未激活，5=退出企业。
    private Integer status;

    /**
     * 主部门
     */
    private Long main_department;


    /**
     * 根据weUser构造 weUserDto
     *
     * @param weUser weUser实体
     */
    public WeUserDTO(WeUser weUser) {
        BeanUtils.copyPropertiesASM(weUser, this);
    }

    public  WeUser transferToWeUser() {
        WeUser weUser = new WeUser();
        BeanUtils.copyPropertiesASM(this,weUser);
        weUser.setAvatarMediaid(this.avatar);
        weUser.setIsLeaderInDept(this.is_leader_in_dept);
        weUser.setUserId(this.userid);
        if (this.getStatus() != null) {
            weUser.setIsActivate(this.getStatus());
        }
        if (this.is_leader_in_dept != null) {
            String[] isLeaderInDeptArr = Arrays.stream(this.is_leader_in_dept)
                    .map(String::valueOf).toArray(String[]::new);
            weUser.setIsLeaderInDept(isLeaderInDeptArr);
        }
        if (this.department != null) {
            String[] departmentsArr = Arrays.stream(this.department)
                    .map(String::valueOf).toArray(String[]::new);
            weUser.setDepartment(departmentsArr);
        }
        if (this.main_department != null) {
            weUser.setMainDepartment(this.main_department);
        }
        return weUser;
    }



}
