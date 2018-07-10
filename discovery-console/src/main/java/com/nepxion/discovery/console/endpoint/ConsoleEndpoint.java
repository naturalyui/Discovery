package com.nepxion.discovery.console.endpoint;

/**
 * <p>Title: Nepxion Discovery</p>
 * <p>Description: Nepxion Discovery</p>
 * <p>Copyright: Copyright (c) 2017-2050</p>
 * <p>Company: Nepxion</p>
 * @author Haojun Ren
 * @version 1.0
 */

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.boot.actuate.endpoint.mvc.MvcEndpoint;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.nepxion.discovery.console.entity.InstanceEntity;

@RestController
@Api(tags = { "控制台接口" })
@ManagedResource(description = "Console Endpoint")
public class ConsoleEndpoint implements MvcEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(ConsoleEndpoint.class);

    @Autowired
    private DiscoveryClient discoveryClient;

    @Autowired
    private RestTemplate consoleRestTemplate;

    @RequestMapping(path = "/console/services", method = RequestMethod.GET)
    @ApiOperation(value = "获取服务注册中心的服务列表", notes = "", response = List.class, httpMethod = "GET")
    @ResponseBody
    @ManagedOperation
    public List<String> services() {
        return getServices();
    }

    @RequestMapping(path = "/console/instances/{serviceId}", method = RequestMethod.GET)
    @ApiOperation(value = "获取服务注册中心服务的实例列表", notes = "", response = List.class, httpMethod = "GET")
    @ResponseBody
    @ManagedOperation
    public List<ServiceInstance> instances(@PathVariable(value = "serviceId") @ApiParam(value = "服务名", required = true) String serviceId) {
        return getInstances(serviceId);
    }

    @RequestMapping(path = "/console/instance-list/{serviceId}", method = RequestMethod.GET)
    @ApiOperation(value = "获取服务注册中心服务的实例列表（精简数据）", notes = "", response = List.class, httpMethod = "GET")
    @ResponseBody
    @ManagedOperation
    public List<InstanceEntity> instanceList(@PathVariable(value = "serviceId") @ApiParam(value = "服务名", required = true) String serviceId) {
        return getInstanceList(serviceId);
    }

    @RequestMapping(path = "/console/instance-map", method = RequestMethod.GET)
    @ApiOperation(value = "获取服务注册中心的服务和实例的Map（精简数据）", notes = "", response = Map.class, httpMethod = "GET")
    @ResponseBody
    @ManagedOperation
    public Map<String, List<InstanceEntity>> instanceMap() {
        return getInstanceMap();
    }

    @RequestMapping(path = "/console/config/send-async/{serviceId}", method = RequestMethod.POST)
    @ApiOperation(value = "批量异步推送规则配置信息", notes = "", response = ResponseEntity.class, httpMethod = "POST")
    @ResponseBody
    @ManagedOperation
    public ResponseEntity<?> configSendAsync(@PathVariable(value = "serviceId") @ApiParam(value = "服务名", required = true) String serviceId, @RequestBody @ApiParam(value = "规则配置内容，XML格式", required = true) String config) {
        return send(serviceId, config, true);
    }

    @RequestMapping(path = "/console/config/send-sync/{serviceId}", method = RequestMethod.POST)
    @ApiOperation(value = "批量同步推送规则配置信息", notes = "", response = ResponseEntity.class, httpMethod = "POST")
    @ResponseBody
    @ManagedOperation
    public ResponseEntity<?> configSendSync(@PathVariable(value = "serviceId") @ApiParam(value = "服务名", required = true) String serviceId, @RequestBody @ApiParam(value = "规则配置内容，XML格式", required = true) String config) {
        return send(serviceId, config, false);
    }

    public List<String> getServices() {
        return discoveryClient.getServices();
    }

    public List<ServiceInstance> getInstances(String serviceId) {
        return discoveryClient.getInstances(serviceId);
    }

    public List<InstanceEntity> getInstanceList(String service) {
        List<ServiceInstance> serviceInstances = getInstances(service);
        List<InstanceEntity> instanceEntityList = new ArrayList<InstanceEntity>(serviceInstances.size());
        for (ServiceInstance serviceInstance : serviceInstances) {
            String serviceId = serviceInstance.getServiceId().toLowerCase();
            String version = serviceInstance.getMetadata().get("version");
            String host = serviceInstance.getHost();
            int port = serviceInstance.getPort();

            InstanceEntity instanceEntity = new InstanceEntity();
            instanceEntity.setServiceId(serviceId);
            instanceEntity.setVersion(version);
            instanceEntity.setHost(host);
            instanceEntity.setPort(port);

            instanceEntityList.add(instanceEntity);
        }

        return instanceEntityList;
    }

    public Map<String, List<InstanceEntity>> getInstanceMap() {
        List<String> services = getServices();
        Map<String, List<InstanceEntity>> serviceMap = new LinkedHashMap<String, List<InstanceEntity>>(services.size());
        for (String service : services) {
            List<InstanceEntity> instanceEntityList = getInstanceList(service);
            serviceMap.put(service, instanceEntityList);
        }

        return serviceMap;
    }

    private ResponseEntity<?> send(String serviceId, String config, boolean async) {
        List<ServiceInstance> serviceInstances = getInstances(serviceId);
        for (ServiceInstance serviceInstance : serviceInstances) {
            String host = serviceInstance.getHost();
            int port = serviceInstance.getPort();

            String url = "http://" + host + ":" + port + "/config/send-" + (async ? "async" : "sync");
            String result = consoleRestTemplate.postForEntity(url, config, String.class).getBody();

            LOG.info("Send rule, serviceId={} url={}, result={}", serviceId, url, result);

            // 这里需要考虑分布式事务
            if (!StringUtils.equals(result, "OK")) {
                return ResponseEntity.ok().body("Send rule failed for url=" + url + ", cause=" + result);
            }
        }

        return ResponseEntity.ok().body("OK");
    }

    @Override
    public String getPath() {
        return "/";
    }

    @Override
    public boolean isSensitive() {
        return true;
    }

    @Override
    public Class<? extends Endpoint<?>> getEndpointType() {
        return null;
    }
}