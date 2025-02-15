package com.easyink.wecom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.easyink.common.enums.ResultTip;
import com.easyink.common.enums.WeEmpleCodeAnalyseTypeEnum;
import com.easyink.common.exception.CustomException;
import com.easyink.common.utils.DateUtils;
import com.easyink.wecom.domain.WeEmpleCodeAnalyse;
import com.easyink.wecom.domain.dto.emplecode.FindWeEmpleCodeAnalyseDTO;
import com.easyink.wecom.domain.vo.WeEmplyCodeAnalyseCountVO;
import com.easyink.wecom.domain.vo.WeEmplyCodeAnalyseVO;
import com.easyink.wecom.mapper.WeEmpleCodeAnalyseMapper;
import com.easyink.wecom.service.WeEmpleCodeAnalyseService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 类名：WeEmpleCodeAnalyseServiceImpl
 *
 * @author Society my sister Li
 * @date 2021-11-04
 */
@Slf4j
@Service
public class WeEmpleCodeAnalyseServiceImpl extends ServiceImpl<WeEmpleCodeAnalyseMapper, WeEmpleCodeAnalyse> implements WeEmpleCodeAnalyseService {


    @Override
    public WeEmplyCodeAnalyseVO getTimeRangeAnalyseCount(FindWeEmpleCodeAnalyseDTO analyseDTO) {
        if (analyseDTO == null || StringUtils.isBlank(analyseDTO.getCorpId()) || StringUtils.isBlank(analyseDTO.getState()) || StringUtils.isBlank(analyseDTO.getBeginTime()) || StringUtils.isBlank(analyseDTO.getEndTime())) {
            throw new CustomException(ResultTip.TIP_GENERAL_BAD_REQUEST);
        }
        //校验开始时间和结束时间格式
        if (!DateUtils.isMatchFormat(analyseDTO.getBeginTime(), DateUtils.YYYY_MM_DD) || !DateUtils.isMatchFormat(analyseDTO.getEndTime(), DateUtils.YYYY_MM_DD)) {
            throw new CustomException(ResultTip.TIP_TIME_FORMAT_ERROR);
        }
        //查询两个时间内的所有日期
        Date startTime = DateUtils.dateTime(DateUtils.YYYY_MM_DD, analyseDTO.getBeginTime());
        Date endTime = DateUtils.dateTime(DateUtils.YYYY_MM_DD, analyseDTO.getEndTime());
        List<Date> dates = DateUtils.findDates(startTime, endTime);

        //查询时间段内新增和流失客户数据
        List<WeEmplyCodeAnalyseCountVO> weEmpleCodeAnalyses = baseMapper.selectCountList(analyseDTO);

        //dataMap <time,WeEmplyCodeAnalyseCountVO>
        Map<String, WeEmplyCodeAnalyseCountVO> dataMap = new HashMap<>(weEmpleCodeAnalyses.size());
        if (CollectionUtils.isNotEmpty(weEmpleCodeAnalyses)) {
            for (WeEmplyCodeAnalyseCountVO weEmplyCodeAnalyseCountVO : weEmpleCodeAnalyses) {
                dataMap.put(weEmplyCodeAnalyseCountVO.getTime(), weEmplyCodeAnalyseCountVO);
            }
        }
        List<WeEmplyCodeAnalyseCountVO> resultList = new ArrayList<>();
        WeEmplyCodeAnalyseCountVO analyseCountVO;
        for (Date date : dates) {
            String time = DateUtils.dateTime(date);
            analyseCountVO = dataMap.get(time);

            //初始化addCount=0、loseCount=0的数据
            if (analyseCountVO == null) {
                analyseCountVO = new WeEmplyCodeAnalyseCountVO();
                analyseCountVO.setTime(time);
            }
            resultList.add(analyseCountVO);
        }
        int total = getAddCountByState(analyseDTO.getState());
        return new WeEmplyCodeAnalyseVO(resultList,total);
    }

    @Override
    public int getAddCountByState(String state){
        if(StringUtils.isBlank(state)){
            throw new CustomException(ResultTip.TIP_GENERAL_BAD_REQUEST);
        }
        LambdaQueryWrapper<WeEmpleCodeAnalyse> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.eq(WeEmpleCodeAnalyse::getEmpleCodeId, state)
                .eq(WeEmpleCodeAnalyse::getType, WeEmpleCodeAnalyseTypeEnum.ADD.getType());
        return baseMapper.selectCount(queryWrapper);
    }


    /**
     * 保存员工活码数据统计
     */
    @Override
    public boolean saveWeEmpleCodeAnalyse(String corpId, String userId, String externalUserId, String state, Boolean addFlag) {
        try {
            if (StringUtils.isBlank(corpId) || StringUtils.isBlank(userId) || StringUtils.isBlank(externalUserId) || StringUtils.isBlank(state) || addFlag == null) {
                throw new CustomException(ResultTip.TIP_GENERAL_BAD_REQUEST);
            }

            WeEmpleCodeAnalyse weEmpleCodeAnalyse = new WeEmpleCodeAnalyse();
            weEmpleCodeAnalyse.setCorpId(corpId);
            weEmpleCodeAnalyse.setEmpleCodeId(Long.parseLong(state));
            weEmpleCodeAnalyse.setUserId(userId);
            weEmpleCodeAnalyse.setExternalUserId(externalUserId);
            weEmpleCodeAnalyse.setTime(new Date());
            weEmpleCodeAnalyse.setType(addFlag);
            baseMapper.insert(weEmpleCodeAnalyse);
            return true;
        } catch (Exception e) {
            log.error("saveWeEmpleCodeAnalyse error!! e={}", ExceptionUtils.getStackTrace(e));
            return false;
        }
    }
}
