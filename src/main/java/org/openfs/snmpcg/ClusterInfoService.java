package org.openfs.snmpcg;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.Handler;
import org.springframework.stereotype.Component;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.Member;

@Component
public class ClusterInfoService {

	private HazelcastInstance instance;

	public ClusterInfoService(HazelcastInstance instance) {
		this.instance = instance;
	}

	@Handler 
	public Map<String,Object> getLeaderStatus() {
		Member member = getLastMember();
		return Collections.singletonMap("isLeader", member.localMember());
	}
	
	@Handler 
	public Map<String,Object> getMaster() {
		Member member = getLastMember();
		return Collections.singletonMap("Master","["+ member.getAddress().getHost()+"]:"+member.getAddress().getPort());
	}
	
	@Handler
	public List<Map<String, Object>> getStatus() {
		return instance.getCluster().getMembers().stream().map(m -> {
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("Host", m.getAddress().getHost());
			map.put("Port", m.getAddress().getPort());
			map.put("Status", m.getUuid().equalsIgnoreCase(getLastMember().getUuid())?"Master":"Slave");
			map.put("isLocal", m.localMember());
			return map;
		}).collect(Collectors.toList());
	}
	
	protected Member getLastMember() {
		return instance.getCluster().getMembers().iterator().next();
	}
}
