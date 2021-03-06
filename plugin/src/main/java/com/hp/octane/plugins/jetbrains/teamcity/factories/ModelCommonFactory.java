/*
 *     2017 EntIT Software LLC, a Micro Focus company, L.P.
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */

package com.hp.octane.plugins.jetbrains.teamcity.factories;

import com.hp.octane.integrations.dto.DTOFactory;
import com.hp.octane.integrations.dto.general.CIJobsList;
import com.hp.octane.integrations.dto.pipelines.PipelineNode;
import com.hp.octane.integrations.dto.pipelines.PipelinePhase;
import com.hp.octane.integrations.dto.snapshots.CIBuildResult;
import com.hp.octane.plugins.jetbrains.teamcity.OctaneTeamCityPlugin;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.dependency.Dependency;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by lazara on 04/01/2016.
 */

public class ModelCommonFactory {
	private static final DTOFactory dtoFactory = DTOFactory.getInstance();

	@Autowired
	private OctaneTeamCityPlugin octaneTeamCityPlugin;
	@Autowired
	private ParametersFactory parametersFactory;

	public CIJobsList CreateProjectList() {
		CIJobsList ciJobsList = dtoFactory.newDTO(CIJobsList.class);
		List<PipelineNode> list = new ArrayList<>();
		List<String> ids = new ArrayList<>();

		PipelineNode buildConf;
		for (SProject project : octaneTeamCityPlugin.getProjectManager().getProjects()) {

			List<SBuildType> buildTypes = project.getBuildTypes();
			for (SBuildType buildType : buildTypes) {
				if (!ids.contains(buildType.getInternalId())) {
					ids.add(buildType.getInternalId());
					buildConf = dtoFactory.newDTO(PipelineNode.class)
							.setJobCiId(buildType.getExternalId())
							.setName(buildType.getName());
					list.add(buildConf);
				}
			}
		}

		ciJobsList.setJobs(list.toArray(new PipelineNode[list.size()]));
		return ciJobsList;
	}

	public PipelineNode createStructure(String buildConfigurationId) {
		SBuildType root = octaneTeamCityPlugin.getProjectManager().findBuildTypeByExternalId(buildConfigurationId);
		PipelineNode treeRoot = null;
		if (root != null) {
			treeRoot = dtoFactory.newDTO(PipelineNode.class)
					.setJobCiId(root.getExternalId())
					.setName(root.getName())
					.setParameters(parametersFactory.obtainFromBuildType(root));

			List<PipelineNode> pipelineNodeList = buildFromDependenciesFlat(root.getOwnDependencies());
			if (!pipelineNodeList.isEmpty()) {
				PipelinePhase phase = dtoFactory.newDTO(PipelinePhase.class)
						.setName("teamcity_dependencies")
						.setBlocking(true)
						.setJobs(pipelineNodeList);
				List<PipelinePhase> pipelinePhaseList = new ArrayList<PipelinePhase>();
				pipelinePhaseList.add(phase);
				treeRoot.setPhasesPostBuild(pipelinePhaseList);
			}
		} else {
			//should update the response?
		}
		return treeRoot;
	}

	private List<PipelineNode> buildFromDependenciesFlat(List<Dependency> dependencies) {
		List<PipelineNode> result = new LinkedList<>();
		if (dependencies != null) {
			for (Dependency dependency : dependencies) {
				SBuildType build = dependency.getDependOn();
				if (build != null) {
					PipelineNode buildItem = dtoFactory.newDTO(PipelineNode.class)
							.setJobCiId(build.getExternalId())
							.setName(build.getName())
							.setParameters(parametersFactory.obtainFromBuildType(build));
					result.add(buildItem);
					result.addAll(buildFromDependenciesFlat(build.getOwnDependencies()));
				}
			}
		}
		return result;
	}

	public CIBuildResult resultFromNativeStatus(Status status) {
		CIBuildResult result = CIBuildResult.UNAVAILABLE;
		if (status == Status.ERROR || status == Status.FAILURE) {
			result = CIBuildResult.FAILURE;
		} else if (status == Status.WARNING) {
			result = CIBuildResult.UNSTABLE;
		} else if (status == Status.NORMAL) {
			result = CIBuildResult.SUCCESS;
		}
		return result;
	}
}
