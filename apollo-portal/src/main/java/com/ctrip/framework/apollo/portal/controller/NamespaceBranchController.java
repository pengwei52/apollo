package com.ctrip.framework.apollo.portal.controller;

import com.ctrip.framework.apollo.common.dto.GrayReleaseRuleDTO;
import com.ctrip.framework.apollo.common.dto.NamespaceDTO;
import com.ctrip.framework.apollo.common.dto.ReleaseDTO;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.core.enums.Env;
import com.ctrip.framework.apollo.portal.component.PermissionValidator;
import com.ctrip.framework.apollo.portal.component.config.PortalConfig;
import com.ctrip.framework.apollo.portal.entity.bo.NamespaceBO;
import com.ctrip.framework.apollo.portal.entity.model.NamespaceReleaseModel;
import com.ctrip.framework.apollo.portal.listener.ConfigPublishEvent;
import com.ctrip.framework.apollo.portal.service.NamespaceBranchService;
import com.ctrip.framework.apollo.portal.service.ReleaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Namespace 分支 Controller
 */
@RestController
public class NamespaceBranchController {

    @Autowired
    private PermissionValidator permissionValidator;
    @Autowired
    private ReleaseService releaseService;
    @Autowired
    private NamespaceBranchService namespaceBranchService;
    @Autowired
    private ApplicationEventPublisher publisher;
    @Autowired
    private PortalConfig portalConfig;

    @RequestMapping(value = "/apps/{appId}/envs/{env}/clusters/{clusterName}/namespaces/{namespaceName}/branches", method = RequestMethod.GET)
    public NamespaceBO findBranch(@PathVariable String appId,
                                  @PathVariable String env,
                                  @PathVariable String clusterName,
                                  @PathVariable String namespaceName) {
        return namespaceBranchService.findBranch(appId, Env.valueOf(env), clusterName, namespaceName);
    }

    // 创建 Namespace 分支
    @PreAuthorize(value = "@permissionValidator.hasModifyNamespacePermission(#appId, #namespaceName)")
    @RequestMapping(value = "/apps/{appId}/envs/{env}/clusters/{clusterName}/namespaces/{namespaceName}/branches", method = RequestMethod.POST)
    public NamespaceDTO createBranch(@PathVariable String appId,
                                     @PathVariable String env,
                                     @PathVariable String clusterName,
                                     @PathVariable String namespaceName) {
        return namespaceBranchService.createBranch(appId, Env.valueOf(env), clusterName, namespaceName);
    }

    @RequestMapping(value = "/apps/{appId}/envs/{env}/clusters/{clusterName}/namespaces/{namespaceName}/branches/{branchName}", method = RequestMethod.DELETE)
    public void deleteBranch(@PathVariable String appId,
                             @PathVariable String env,
                             @PathVariable String clusterName,
                             @PathVariable String namespaceName,
                             @PathVariable String branchName) {

        boolean canDelete = permissionValidator.hasReleaseNamespacePermission(appId, namespaceName) ||
                (permissionValidator.hasModifyNamespacePermission(appId, namespaceName) &&
                        releaseService.loadLatestRelease(appId, Env.valueOf(env), branchName, namespaceName) == null);


        if (!canDelete) {
            throw new AccessDeniedException("Forbidden operation. "
                    + "Caused by: 1.you don't have release permission "
                    + "or 2. you don't have modification permission "
                    + "or 3. you have modification permission but branch has been released");
        }

        namespaceBranchService.deleteBranch(appId, Env.valueOf(env), clusterName, namespaceName, branchName);

    }

    // 灰度全量发布
    @PreAuthorize(value = "@permissionValidator.hasReleaseNamespacePermission(#appId, #namespaceName)")
    @RequestMapping(value = "/apps/{appId}/envs/{env}/clusters/{clusterName}/namespaces/{namespaceName}/branches/{branchName}/merge", method = RequestMethod.POST)
    public ReleaseDTO merge(@PathVariable String appId, @PathVariable String env,
                            @PathVariable String clusterName, @PathVariable String namespaceName,
                            @PathVariable String branchName, @RequestParam(value = "deleteBranch", defaultValue = "true") boolean deleteBranch,
                            @RequestBody NamespaceReleaseModel model) {
        // 若是紧急发布，但是当前环境未允许该操作，抛出 BadRequestException 异常
        if (model.isEmergencyPublish() && !portalConfig.isEmergencyPublishAllowed(Env.fromString(env))) {
            throw new BadRequestException(String.format("Env: %s is not supported emergency publish now", env));
        }
        // 合并子 Namespace 变更的配置 Map 到父 Namespace ，并进行一次 Release
        ReleaseDTO createdRelease = namespaceBranchService.merge(appId, Env.valueOf(env), clusterName, namespaceName, branchName,
                model.getReleaseTitle(), model.getReleaseComment(),
                model.isEmergencyPublish(), deleteBranch);

        // 创建 ConfigPublishEvent 对象
        ConfigPublishEvent event = ConfigPublishEvent.instance();
        event.withAppId(appId)
                .withCluster(clusterName)
                .withNamespace(namespaceName)
                .withReleaseId(createdRelease.getId())
                .setMergeEvent(true)
                .setEnv(Env.valueOf(env));
        // 发布 ConfigPublishEvent 事件
        publisher.publishEvent(event);
        return createdRelease;
    }

    @RequestMapping(value = "/apps/{appId}/envs/{env}/clusters/{clusterName}/namespaces/{namespaceName}/branches/{branchName}/rules", method = RequestMethod.GET)
    public GrayReleaseRuleDTO getBranchGrayRules(@PathVariable String appId, @PathVariable String env,
                                                 @PathVariable String clusterName,
                                                 @PathVariable String namespaceName,
                                                 @PathVariable String branchName) {

        return namespaceBranchService.findBranchGrayRules(appId, Env.valueOf(env), clusterName, namespaceName, branchName);
    }

    // 更新 Namespace 分支的灰度规则
    @PreAuthorize(value = "@permissionValidator.hasOperateNamespacePermission(#appId, #namespaceName)")
    @RequestMapping(value = "/apps/{appId}/envs/{env}/clusters/{clusterName}/namespaces/{namespaceName}/branches/{branchName}/rules", method = RequestMethod.PUT)
    public void updateBranchRules(@PathVariable String appId, @PathVariable String env,
                                  @PathVariable String clusterName, @PathVariable String namespaceName,
                                  @PathVariable String branchName, @RequestBody GrayReleaseRuleDTO rules) {
        namespaceBranchService.updateBranchGrayRules(appId, Env.valueOf(env), clusterName, namespaceName, branchName, rules);
    }

}
