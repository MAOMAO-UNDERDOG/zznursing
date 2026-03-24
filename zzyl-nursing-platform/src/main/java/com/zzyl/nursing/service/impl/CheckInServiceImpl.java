package com.zzyl.nursing.service.impl;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import cn.hutool.core.util.IdcardUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zzyl.common.utils.CodeGenerator;
import com.zzyl.common.utils.DateUtils;
import com.zzyl.common.utils.bean.BeanUtils;
import com.zzyl.nursing.domain.*;
import com.zzyl.nursing.dto.CheckInApplyDto;
import com.zzyl.nursing.dto.CheckInElderDto;
import com.zzyl.nursing.mapper.BedMapper;
import com.zzyl.nursing.mapper.ContractMapper;
import com.zzyl.nursing.mapper.ElderMapper;
import com.zzyl.nursing.mapper.CheckInConfigMapper;
import com.zzyl.nursing.vo.CheckInConfigVo;
import com.zzyl.nursing.vo.CheckInDetailVo;
import com.zzyl.nursing.vo.CheckInElderVo;
import nonapi.io.github.classgraph.json.Id;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.zzyl.nursing.mapper.CheckInMapper;
import com.zzyl.nursing.service.ICheckInService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.transaction.annotation.Transactional;

/**
 * 入住Service业务层处理
 * 
 * @author alexis
 * @date 2026-03-20
 */
@Service
public class CheckInServiceImpl extends ServiceImpl<CheckInMapper, CheckIn> implements ICheckInService
{
    @Autowired
    private CheckInMapper checkInMapper;
    @Autowired
    private ElderMapper elderMapper;
    @Autowired
    private BedMapper bedMapper;
    @Autowired
    private ContractMapper contractMapper;
    @Autowired
    private CheckInConfigMapper checkInConfigMapper;

    /**
     * 查询入住
     * 
     * @param id 入住主键
     * @return 入住
     */
    @Override
    public CheckIn selectCheckInById(Long id)
    {
        return getById(id);
    }

    /**
     * 查询入住列表
     * 
     * @param checkIn 入住
     * @return 入住
     */
    @Override
    public List<CheckIn> selectCheckInList(CheckIn checkIn)
    {
        return checkInMapper.selectCheckInList(checkIn);
    }

    /**
     * 新增入住
     * 
     * @param checkIn 入住
     * @return 结果
     */
    @Override
    public int insertCheckIn(CheckIn checkIn)
    {
        return save(checkIn) ? 1 : 0;
    }

    /**
     * 修改入住
     * 
     * @param checkIn 入住
     * @return 结果
     */
    @Override
    public int updateCheckIn(CheckIn checkIn)
    {
        return updateById(checkIn) ? 1 : 0;
    }

    /**
     * 批量删除入住
     * 
     * @param ids 需要删除的入住主键
     * @return 结果
     */
    @Override
    public int deleteCheckInByIds(Long[] ids)
    {
        return removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除入住信息
     * 
     * @param id 入住主键
     * @return 结果
     */
    @Override
    public int deleteCheckInById(Long id)
    {
        return removeById(id) ? 1 : 0;
    }

    /**
     * 入住申请
     *
     * @param checkInApplyDto 入住申请对象
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void apply(CheckInApplyDto checkInApplyDto) {
        //校验老人是否已入住，如果已入住，抛出异常
        LambdaQueryWrapper<Elder> elderQueryWrapper = new LambdaQueryWrapper<Elder>().eq(Elder::getIdCardNo, checkInApplyDto.getCheckInElderDto().getIdCardNo())
                .in(Elder::getStatus, 1, 4);
        Elder elder = elderMapper.selectOne(elderQueryWrapper);
        if (ObjectUtil.isNotEmpty(elder)) {
            throw new RuntimeException("老人已入住, 请勿重复入住");
        }
        //更新床位状态为已入住(查询床位，更新床位状态)
        Bed bed = bedMapper.selectBedById(checkInApplyDto.getCheckInConfigDto().getBedId());
        bed.setBedStatus(1);
        bedMapper.updateById(bed);
        //新增或者更新老人基本信息
        elder = insertOrUpdateElder(bed, checkInApplyDto.getCheckInElderDto());
        //生成一个合同编号
        String contractNo = "HT" + CodeGenerator.generateContractNumber();
        //新增签约办理
        insertContract(contractNo, elder, checkInApplyDto);
        //新增入住信息
        CheckIn checkIn = insertCheckIn(elder, checkInApplyDto);
        //新增入住配置
        insertCheckInConfig(checkIn.getId(), checkInApplyDto);
    }

    /**
     * 入住详情
     *
     * @param id 入住主键
     * @return 入住
     */
    @Override
    public CheckInDetailVo detail(Long id) {
        CheckInDetailVo checkInDetailVo = new CheckInDetailVo();
        // 查询老人信息
        Long elderId = checkInMapper.selectCheckInById(id).getElderId();
        Elder elder = elderMapper.selectElderById(elderId);
        // 创建老人 VO 对象并拷贝属性
        CheckInElderVo checkInElderVo = new CheckInElderVo();
        BeanUtils.copyProperties(elder, checkInElderVo);
        checkInElderVo.setAge(IdcardUtil.getAgeByIdCard(elder.getIdCardNo()));
        checkInDetailVo.setCheckInElderVo(checkInElderVo);
        // 查询入住配置信息
        CheckInConfig checkInConfig = checkInConfigMapper.selectCheckInConfigByCheckInId(id);
        Contract contract = contractMapper.selectContractByElderId(elderId);
        CheckInConfigVo checkInConfigVo = new CheckInConfigVo();
        BeanUtils.copyProperties(checkInConfig, checkInConfigVo);
        checkInConfigVo.setStartDate(contract.getStartDate());
        checkInConfigVo.setEndDate(contract.getEndDate());
        checkInConfigVo.setBedNumber(elder.getBedNumber());
        checkInDetailVo.setCheckInConfigVo(checkInConfigVo);
        // 查询合同信息
        checkInDetailVo.setContract(contract);
        // 查询家属信息
        checkInDetailVo.setElderFamilyVoList(JSON.parseObject(checkInMapper.selectCheckInById(id).getRemark(), List.class));
        return checkInDetailVo;
    }

    /**
     * 新增或更新老人信息
     * @param bed
     * @param checkInElderDto
     * @return
     */
    private Elder insertOrUpdateElder(Bed bed, CheckInElderDto checkInElderDto) {
        // 准备一个Elder对象
        Elder elder = new Elder();
        // 属性拷贝
        BeanUtils.copyProperties(checkInElderDto, elder);
        elder.setBedId(bed.getId());
        elder.setBedNumber(bed.getBedNumber());
        elder.setStatus(1);
        // 查询老人信息
        LambdaQueryWrapper<Elder> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Elder::getIdCardNo, elder.getIdCardNo());
        lambdaQueryWrapper.notIn(Elder::getStatus, 1, 4);
        Elder elderInDb = elderMapper.selectOne(lambdaQueryWrapper);
        if (ObjectUtil.isNotEmpty(elderInDb)) {
            // 修改
            elder.setId(elderInDb.getId());
            elderMapper.updateById(elder);
        } else {
            // 新增
            elderMapper.insert(elder);
        }
        return elder;
    }
    /**
     * 新增合同
     * @param contractNo
     * @param elder
     * @param checkInApplyDto
     */
    private void insertContract(String contractNo, Elder elder, CheckInApplyDto checkInApplyDto) {

        Contract contract = new Contract();
        // 属性拷贝
        BeanUtils.copyProperties(checkInApplyDto.getCheckInContractDto(), contract);
        contract.setContractNumber(contractNo);
        contract.setElderId(elder.getId());
        contract.setElderName(elder.getName());
        // 状态、开始时间、结束时间
        // 签约时间小于等于当前时间，合同生效中
        LocalDateTime checkInStartTime = checkInApplyDto.getCheckInConfigDto().getStartDate();
        LocalDateTime checkInEndTime = checkInApplyDto.getCheckInConfigDto().getEndDate();
        Integer status = checkInStartTime.isAfter(LocalDateTime.now()) ? 0 : 1;
        contract.setStatus(status);
        contract.setStartDate(checkInStartTime);
        contract.setEndDate(checkInEndTime);
        contractMapper.insert(contract);
    }
    /**
     * 新增入住信息
     * @param elder
     * @param checkInApplyDto
     */
    private CheckIn insertCheckIn(Elder elder, CheckInApplyDto checkInApplyDto) {
        CheckIn checkIn = new CheckIn();
        checkIn.setElderId(elder.getId());
        checkIn.setElderName(elder.getName());
        checkIn.setIdCardNo(elder.getIdCardNo());
        checkIn.setNursingLevelName(checkInApplyDto.getCheckInConfigDto().getNursingLevelName());
        checkIn.setStartDate(checkInApplyDto.getCheckInConfigDto().getStartDate());
        checkIn.setEndDate(checkInApplyDto.getCheckInConfigDto().getEndDate());
        checkIn.setBedNumber(elder.getBedNumber());
        checkIn.setRemark(JSON.toJSONString(checkInApplyDto.getElderFamilyDtoList()));
        checkIn.setStatus(0);
        checkInMapper.insert(checkIn);
        return checkIn;
    }
    /**
     * 新增入住配置
     * @param checkInApplyDto
     */
    private void insertCheckInConfig(Long checkInId, CheckInApplyDto checkInApplyDto) {
        CheckInConfig checkInConfig = new CheckInConfig();
        BeanUtils.copyProperties(checkInApplyDto.getCheckInConfigDto(), checkInConfig);
        checkInConfig.setCheckInId(checkInId);
        checkInConfigMapper.insert(checkInConfig);
    }

}
