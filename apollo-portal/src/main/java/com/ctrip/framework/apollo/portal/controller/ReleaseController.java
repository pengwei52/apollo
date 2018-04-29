package com.ctrip.framework.apollo.portal.controller;

import com.ctrip.framework.apollo.common.dto.ReleaseDTO;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.utils.RequestPrecondition;
import com.ctrip.framework.apollo.core.enums.Env;
import com.ctrip.framework.apollo.portal.component.config.PortalConfig;
import com.ctrip.framework.apollo.portal.entity.bo.ReleaseBO;
import com.ctrip.framework.apollo.portal.entity.model.NamespaceReleaseModel;
import com.ctrip.framework.apollo.portal.entity.vo.ReleaseCompareResult;
import com.ctrip.framework.apollo.portal.listener.ConfigPublishEvent;
import com.ctrip.framework.apollo.portal.service.ReleaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

import static com.ctrip.framework.apollo.common.utils.RequestPrecondition.checkModel;

/**
 * Release Controller
 */
@RestController
public class ReleaseController {

    @Autowired
    private ReleaseService releaseService;
    @Autowired
    private ApplicationEventPublisher publisher;
    @Autowired
    private PortalConfig portalConfig;

    /**
     * 发布配置
     *
     * @param appId App 编号
     * @param env Env 名字
     * @param clusterName Cluster 名字
     * @param namespaceName Namespace 名字
     * @param model NamespaceReleaseModel 对象
     * @return 保存的 ReleaseDTO 对象
     */
    @PreAuthorize(value = "@permissionValidator.hasReleaseNamespacePermission(#appId, #namespaceName)")
    @RequestMapping(value = "/apps/{appId}/envs/{env}/clusters/{clusterName}/namespaces/{namespaceName}/releases", method = RequestMethod.POST)
    public ReleaseDTO createRelease(@PathVariable String appId,
                                    @PathVariable String env, @PathVariable String clusterName,
                                    @PathVariable String namespaceName, @RequestBody NamespaceReleaseModel model) {

        // 校验 NamespaceReleaseModel 非空
        checkModel(Objects.nonNull(model));
        // 设置 PathVariable 变量到 NamespaceReleaseModel 中
        model.setAppId(appId);
        model.setEnv(env);
        model.setClusterName(clusterName);
        model.setNamespaceName(namespaceName);
        // 若是紧急发布，但是当前环境未允许该操作，抛出 BadRequestException 异常
        if (model.isEmergencyPublish() && !portalConfig.isEmergencyPublishAllowed(Env.valueOf(env))) {
            throw new BadRequestException(String.format("Env: %s is not supported emergency publish now", env));
        }
        // 发布配置
        ReleaseDTO createdRelease = releaseService.publish(model);

        // 创建 ConfigPublishEvent 对象
        ConfigPublishEvent event = ConfigPublishEvent.instance();
        event.withAppId(appId)
                .withCluster(clusterName)
                .withNamespace(namespaceName)
                .withReleaseId(createdRelease.getId())
                .setNormalPublishEvent(true)
                .setEnv(Env.valueOf(env));
        // 发布 ConfigPublishEvent 事件
        publisher.publishEvent(event);

        return createdRelease;
    }

    @PreAuthorize(value = "@permissionValidator.hasReleaseNamespacePermission(#appId, #namespaceName)")
    @RequestMapping(value = "/apps/{appId}/envs/{env}/clusters/{clusterName}/namespaces/{namespaceName}/branches/{branchName}/releases",
            method = RequestMethod.POST)
    public ReleaseDTO createGrayRelease(@PathVariable String appId,
                                        @PathVariable String env, @PathVariable String clusterName,
                                        @PathVariable String namespaceName, @PathVariable String branchName,
                                        @RequestBody NamespaceReleaseModel model) {

        checkModel(Objects.nonNull(model));
        model.setAppId(appId);
        model.setEnv(env);
        model.setClusterName(branchName);
        model.setNamespaceName(namespaceName);

        if (model.isEmergencyPublish() && !portalConfig.isEmergencyPublishAllowed(Env.valueOf(env))) {
            throw new BadRequestException(String.format("Env: %s is not supported emergency publish now", env));
        }

        ReleaseDTO createdRelease = releaseService.publish(model);

        ConfigPublishEvent event = ConfigPublishEvent.instance();
        event.withAppId(appId)
                .withCluster(clusterName)
                .withNamespace(namespaceName)
                .withReleaseId(createdRelease.getId())
                .setGrayPublishEvent(true)
                .setEnv(Env.valueOf(env));

        publisher.publishEvent(event);

        return createdRelease;
    }


    @RequestMapping(value = "/apps/{appId}/envs/{env}/clusters/{clusterName}/namespaces/{namespaceName}/releases/all", method = RequestMethod.GET)
    public List<ReleaseBO> findAllReleases(@PathVariable String appId,
                                           @PathVariable String env,
                                           @PathVariable String clusterName,
                                           @PathVariable String namespaceName,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "5") int size) {

        RequestPrecondition.checkNumberPositive(size);
        RequestPrecondition.checkNumberNotNegative(page);

        return releaseService.findAllReleases(appId, Env.valueOf(env), clusterName, namespaceName, page, size);
    }

    @RequestMapping(value = "/apps/{appId}/envs/{env}/clusters/{clusterName}/namespaces/{namespaceName}/releases/active", method = RequestMethod.GET)
    public List<ReleaseDTO> findActiveReleases(@PathVariable String appId,
                                               @PathVariable String env,
                                               @PathVariable String clusterName,
                                               @PathVariable String namespaceName,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "5") int size) {

        RequestPrecondition.checkNumberPositive(size);
        RequestPrecondition.checkNumberNotNegative(page);

        return releaseService.findActiveReleases(appId, Env.valueOf(env), clusterName, namespaceName, page, size);
    }

    @RequestMapping(value = "/envs/{env}/releases/compare", method = RequestMethod.GET)
    public ReleaseCompareResult compareRelease(@PathVariable String env,
                                               @RequestParam long baseReleaseId,
                                               @RequestParam long toCompareReleaseId) {

        return releaseService.compare(Env.valueOf(env), baseReleaseId, toCompareReleaseId);
    }


    @RequestMapping(path = "/envs/{env}/releases/{releaseId}/rollback", method = RequestMethod.PUT)
    public void rollback(@PathVariable String env,
                         @PathVariable long releaseId) {
        releaseService.rollback(Env.valueOf(env), releaseId);
        ReleaseDTO release = releaseService.findReleaseById(Env.valueOf(env), releaseId);
        if (Objects.isNull(release)) {
            return;
        }

        ConfigPublishEvent event = ConfigPublishEvent.instance();
        event.withAppId(release.getAppId())
                .withCluster(release.getClusterName())
                .withNamespace(release.getNamespaceName())
                .withPreviousReleaseId(releaseId)
                .setRollbackEvent(true)
                .setEnv(Env.valueOf(env));

        publisher.publishEvent(event);
    }
}
