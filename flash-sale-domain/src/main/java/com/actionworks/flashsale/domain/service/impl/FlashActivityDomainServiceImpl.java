package com.actionworks.flashsale.domain.service.impl;

import com.actionworks.flashsale.domain.event.DomainEventPublisher;
import com.actionworks.flashsale.domain.event.FlashActivityEvent;
import com.actionworks.flashsale.domain.event.FlashActivityEventType;
import com.actionworks.flashsale.domain.exception.DomainException;
import com.actionworks.flashsale.domain.model.PageResult;
import com.actionworks.flashsale.domain.model.PagesQueryCondition;
import com.actionworks.flashsale.domain.model.entity.FlashActivity;
import com.actionworks.flashsale.domain.model.enums.FlashActivityStatus;
import com.actionworks.flashsale.domain.repository.FlashActivityRepository;
import com.actionworks.flashsale.domain.service.FlashActivityDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;

import static com.actionworks.flashsale.domain.exception.DomainErrorCode.FLASH_ACTIVITY_DOES_NOT_EXIST;
import static com.actionworks.flashsale.domain.exception.DomainErrorCode.FLASH_ACTIVITY_NOT_ONLINE;
import static com.actionworks.flashsale.domain.exception.DomainErrorCode.ONLINE_FLASH_ACTIVITY_PARAMS_INVALID;
import static com.actionworks.flashsale.domain.exception.DomainErrorCode.PARAMS_INVALID;

@Service
public class FlashActivityDomainServiceImpl implements FlashActivityDomainService {
    private static final Logger logger = LoggerFactory.getLogger(FlashActivityDomainServiceImpl.class);
    @Resource
    private FlashActivityRepository flashActivityRepository;

    @Resource
    private DomainEventPublisher domainEventPublisher;

    @Override
    public void publishActivity(Long userId, FlashActivity flashActivity) {
        logger.info("Preparing to publish flash activity:{},{}", userId, flashActivity);
        if (flashActivity == null || !flashActivity.validateParamsForCreateOrUpdate()) {
            throw new DomainException(ONLINE_FLASH_ACTIVITY_PARAMS_INVALID);
        }
        flashActivity.setStatus(FlashActivityStatus.PUBLISHED.getCode());
        flashActivityRepository.save(flashActivity);
        logger.info("Flash activity was published:{},{}", userId, flashActivity.getId());

        FlashActivityEvent flashActivityEvent = new FlashActivityEvent();
        flashActivityEvent.setEventType(FlashActivityEventType.PUBLISHED);
        flashActivityEvent.setFlashActivity(flashActivity);
        domainEventPublisher.publish(flashActivityEvent);
    }

    @Override
    public void modifyActivity(Long userId, FlashActivity flashActivity) {
        logger.info("Preparing to modify flash activity:{},{}", userId, flashActivity);
        if (flashActivity == null || !flashActivity.validateParamsForCreateOrUpdate()) {
            throw new DomainException(ONLINE_FLASH_ACTIVITY_PARAMS_INVALID);
        }
        flashActivityRepository.save(flashActivity);
        logger.info("Flash activity was modified:{},{}", userId, flashActivity.getId());

        FlashActivityEvent flashActivityEvent = new FlashActivityEvent();
        flashActivityEvent.setEventType(FlashActivityEventType.MODIFIED);
        flashActivityEvent.setFlashActivity(flashActivity);
        domainEventPublisher.publish(flashActivityEvent);
    }

    @Override
    public void onlineActivity(Long userId, Long activityId) {
        logger.info("Preparing to online flash activity:{},{}", userId, activityId);
        if (StringUtils.isEmpty(userId) || activityId == null) {
            throw new DomainException(PARAMS_INVALID);
        }
        Optional<FlashActivity> flashActivityOptional = flashActivityRepository.findById(activityId);
        if (!flashActivityOptional.isPresent()) {
            throw new DomainException(FLASH_ACTIVITY_DOES_NOT_EXIST);
        }
        FlashActivity flashActivity = flashActivityOptional.get();
        if (FlashActivityStatus.isOnline(flashActivity.getStatus())) {
            return;
        }
        flashActivity.setStatus(FlashActivityStatus.ONLINE.getCode());
        flashActivityRepository.save(flashActivity);
        logger.info("Flash activity was online:{},{}", userId, activityId);

        FlashActivityEvent flashActivityEvent = new FlashActivityEvent();
        flashActivityEvent.setEventType(FlashActivityEventType.ONLINE);
        flashActivityEvent.setFlashActivity(flashActivity);
        domainEventPublisher.publish(flashActivityEvent);
    }

    @Override
    public void offlineActivity(Long userId, Long activityId) {
        logger.info("Preparing to offline flash activity:{},{}", userId, activityId);
        if (StringUtils.isEmpty(userId) || activityId == null) {
            throw new DomainException(PARAMS_INVALID);
        }
        Optional<FlashActivity> flashActivityOptional = flashActivityRepository.findById(activityId);
        if (!flashActivityOptional.isPresent()) {
            throw new DomainException(FLASH_ACTIVITY_DOES_NOT_EXIST);
        }
        FlashActivity flashActivity = flashActivityOptional.get();
        if (FlashActivityStatus.isOffline(flashActivity.getStatus())) {
            return;
        }
        if (!FlashActivityStatus.isOnline(flashActivity.getStatus())) {
            throw new DomainException(FLASH_ACTIVITY_NOT_ONLINE);
        }
        flashActivity.setStatus(FlashActivityStatus.OFFLINE.getCode());
        flashActivityRepository.save(flashActivity);
        logger.info("Flash activity was offline:{},{}", userId, activityId);

        FlashActivityEvent flashActivityEvent = new FlashActivityEvent();
        flashActivityEvent.setEventType(FlashActivityEventType.OFFLINE);
        flashActivityEvent.setFlashActivity(flashActivity);
        domainEventPublisher.publish(flashActivityEvent);
    }

    @Override
    public PageResult<FlashActivity> getFlashActivities(PagesQueryCondition pagesQueryCondition) {
        if (pagesQueryCondition == null) {
            pagesQueryCondition = new PagesQueryCondition();
        }
        List<FlashActivity> flashActivities = flashActivityRepository.findFlashActivitiesByCondition(pagesQueryCondition.buildParams());
        Integer total = flashActivityRepository.countFlashActivitiesByCondition(pagesQueryCondition);
        logger.info("Get flash activities:{},{}", flashActivities.size());
        return PageResult.with(flashActivities, total);
    }

    @Override
    public FlashActivity getFlashActivity(Long activityId) {
        if (activityId == null) {
            throw new DomainException(PARAMS_INVALID);
        }
        Optional<FlashActivity> flashActivityOptional = flashActivityRepository.findById(activityId);
        return flashActivityOptional.orElse(null);
    }

    @Override
    public boolean isAllowPlaceOrderOrNot(Long activityId) {
        Optional<FlashActivity> flashActivityOptional = flashActivityRepository.findById(activityId);
        if (!flashActivityOptional.isPresent()) {
            logger.info("isAllowPlaceOrderOrNot|活动不存在:{}", activityId);
            return false;
        }
        FlashActivity flashActivity = flashActivityOptional.get();
        if (!flashActivity.isOnline()) {
            logger.info("isAllowPlaceOrderOrNot|活动尚未上线:{}", activityId);
            return false;
        }
        if (!flashActivity.isInProgress()) {
            logger.info("isAllowPlaceOrderOrNot|活动非秒杀时段:{}", activityId);
            return false;
        }
        return true;
    }
}