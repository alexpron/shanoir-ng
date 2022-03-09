/**
 * Shanoir NG - Import, manage and share neuroimaging data
 * Copyright (C) 2009-2019 Inria - https://www.inria.fr/
 * Contact us on https://project.inria.fr/shanoir/
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see https://www.gnu.org/licenses/gpl-3.0.html
 */

/**
 * NOTE: This class is auto generated by the swagger code generator program (2.2.3).
 * https://github.com/swagger-api/swagger-codegen
 * Do not edit the class manually.
 */
package org.shanoir.ng.solr.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.shanoir.ng.shared.dateTime.DateTimeUtils;
import org.shanoir.ng.shared.exception.RestServiceException;
import org.shanoir.ng.shared.model.SubjectStudy;
import org.shanoir.ng.shared.model.Tag;
import org.shanoir.ng.shared.paging.PageImpl;
import org.shanoir.ng.shared.repository.SubjectStudyRepository;
import org.shanoir.ng.shared.security.rights.StudyUserRight;
import org.shanoir.ng.solr.model.ShanoirMetadata;
import org.shanoir.ng.solr.model.ShanoirSolrDocument;
import org.shanoir.ng.solr.model.ShanoirSolrQuery;
import org.shanoir.ng.solr.repository.ShanoirMetadataRepository;
import org.shanoir.ng.solr.repository.SolrRepository;
import org.shanoir.ng.study.rights.StudyUserRightsRepository;
import org.shanoir.ng.utils.KeycloakUtil;
import org.shanoir.ng.utils.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.solr.core.query.result.SolrResultPage;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * @author yyao
 *
 */
@Service
public class SolrServiceImpl implements SolrService {

	//	private static final Logger LOG = LoggerFactory.getLogger(SolrServiceImpl.class);

	@Autowired
	private SolrRepository solrRepository;

	@Autowired
	private ShanoirMetadataRepository shanoirMetadataRepository;

	@Autowired
	private StudyUserRightsRepository rightsRepository;

	@Autowired
	private SubjectStudyRepository subjectStudyRepo;

	@Transactional
	@Override
	public void addToIndex (final ShanoirSolrDocument document) {
		solrRepository.save(document);
	}

	@Transactional
	@Override
	public void addAllToIndex (final List<ShanoirSolrDocument> documents) {
		solrRepository.saveAll(documents);
	}

	@Transactional
	@Override
	public void deleteFromIndex(Long datasetId) {
		solrRepository.deleteByDatasetId(datasetId);
	}

	@Transactional
	@Override
	public void deleteFromIndex(List<Long> datasetIds) {
		solrRepository.deleteByDatasetIdIn(datasetIds);
	}

	@Transactional
	public void deleteAll() {
		solrRepository.deleteAll();
	}

	@Transactional
	@Override
	@Scheduled(cron = "0 0 6 * * *", zone="Europe/Paris")
	public void indexAll() {
		// 1. delete all
		deleteAll();

		// 2. get all datasets
		List<ShanoirMetadata> documents = shanoirMetadataRepository.findAllAsSolrDoc();
		indexDocumentsInSolr(documents);
	}

	@Transactional
	@Override
	public void indexDatasets(List<Long> datasetIds) {
		// Get all associated datasets and index them to solr
		List<ShanoirMetadata> shanoirMetadatas = shanoirMetadataRepository.findSolrDocs(datasetIds);
		indexDocumentsInSolr(shanoirMetadatas);
	}

	@Override
	@Transactional(isolation = Isolation.READ_UNCOMMITTED,  propagation = Propagation.REQUIRES_NEW)
	public void indexDataset(Long datasetId) {
		ShanoirMetadata shanoirMetadata = shanoirMetadataRepository.findOneSolrDoc(datasetId);
		if (shanoirMetadata == null) throw new IllegalStateException("shanoir metadata with id " +  datasetId + " query failed to return any result");
		ShanoirSolrDocument doc = getShanoirSolrDocument(shanoirMetadata);

		// Get tags
		List<SubjectStudy> list = subjectStudyRepo.findByStudyIdInAndSubjectIdIn(Collections.singletonList(shanoirMetadata.getStudyId()), Collections.singletonList(shanoirMetadata.getSubjectId()));

		List<String> tags = new ArrayList<>();
		if (list != null) {
			for (SubjectStudy susu : list) {
				if (susu.getTags() != null) {
					for (Tag tag : susu.getTags()) {
						tags.add(tag.getName());
					}
				}
			}
		}

		doc.setTags(tags);

		solrRepository.save(doc);
	}

	private void indexDocumentsInSolr(List<ShanoirMetadata> metadatas) {
		Iterator<ShanoirMetadata> docIt = metadatas.iterator();

		List<ShanoirSolrDocument> solrDocuments = new ArrayList<>();

		while (docIt.hasNext()) {
			ShanoirMetadata shanoirMetadata = docIt.next();
			ShanoirSolrDocument doc = getShanoirSolrDocument(shanoirMetadata);
			solrDocuments.add(doc);
		}

		if (CollectionUtils.isEmpty(solrDocuments)) {
			return;
		}

		List<SubjectStudy> subjstuds = Utils.toList(subjectStudyRepo.findAll());

		Map<Long, Map<String, List<Tag>>> tags = new HashMap<>();

		for (SubjectStudy subjstud : subjstuds) {
			if (tags.get(subjstud.getStudy().getId()) == null) {
				tags.put(subjstud.getStudy().getId(), new HashMap<>());
			}
			tags.get(subjstud.getStudy().getId()).put(subjstud.getSubject().getName(), subjstud.getTags() != null ? subjstud.getTags() : Collections.emptyList());
		}
		
		// Update tags
		for (ShanoirSolrDocument doc : solrDocuments) {
			if (doc != null && tags != null && tags.get(doc.getStudyId()) != null) {
				List<Tag> list = tags.get(doc.getStudyId()).get(doc.getSubjectName());
				if (list != null && !list.isEmpty()) {
					doc.setTags(list.stream().map(Tag::getName).collect(Collectors.toList()));
				}				
			}
		}

		this.addAllToIndex(solrDocuments);
	}

	private ShanoirSolrDocument getShanoirSolrDocument(ShanoirMetadata shanoirMetadata) {
		return new ShanoirSolrDocument(String.valueOf(shanoirMetadata.getDatasetId()), shanoirMetadata.getDatasetId(), shanoirMetadata.getDatasetName(),
				shanoirMetadata.getDatasetType(), shanoirMetadata.getDatasetNature(), DateTimeUtils.localDateToDate(shanoirMetadata.getDatasetCreationDate()),
				shanoirMetadata.getExaminationComment(), DateTimeUtils.localDateToDate(shanoirMetadata.getExaminationDate()),
				shanoirMetadata.getSubjectName(), shanoirMetadata.getStudyName(), shanoirMetadata.getStudyId(), shanoirMetadata.getCenterName(),
				shanoirMetadata.getSliceThickness(), shanoirMetadata.getPixelBandwidth(), shanoirMetadata.getMagneticFieldStrength());
	}

	@Transactional
	@Override
	public SolrResultPage<ShanoirSolrDocument> facetSearch(ShanoirSolrQuery query, Pageable pageable) throws RestServiceException {
		SolrResultPage<ShanoirSolrDocument> result = null;
		pageable = prepareTextFields(pageable);
		if (KeycloakUtil.getTokenRoles().contains("ROLE_ADMIN")) {
			result = solrRepository.findByFacetCriteria(query, pageable);
		} else {
			List<Long> studyIds = rightsRepository.findDistinctStudyIdByUserId(KeycloakUtil.getTokenUserId(), StudyUserRight.CAN_SEE_ALL.getId());
			result = solrRepository.findByStudyIdInAndFacetCriteria(studyIds, query, pageable);
		}
		return result;
	}

	private Pageable prepareTextFields(Pageable pageable) {
		for (Sort.Order order : pageable.getSort()) {
			if (order.getProperty().equals("studyName") || order.getProperty().equals("subjectName")
					|| order.getProperty().equals("datasetName") || order.getProperty().equals("datasetNature")
					|| order.getProperty().equals("datasetType") || order.getProperty().equals("examinationComment")
					|| order.getProperty().equals("tags")) {
				pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
						order.getDirection(), order.getProperty());
			} else if (order.getProperty().equals("id")) {
				pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
						order.getDirection(), "datasetId");
			}
		}
		return pageable;
	}

	@Override
	public Page<ShanoirSolrDocument> getByIdIn(List<Long> datasetIds, Pageable pageable) {
		if (datasetIds.isEmpty()) {
			return new PageImpl<>();
		}
		Page<ShanoirSolrDocument> result;
		pageable = prepareTextFields(pageable);
		if (KeycloakUtil.getTokenRoles().contains("ROLE_ADMIN")) {
			result = solrRepository.findByDatasetIdIn(datasetIds, pageable);
		} else {
			List<Long> studyIds = rightsRepository.findDistinctStudyIdByUserId(KeycloakUtil.getTokenUserId(), StudyUserRight.CAN_SEE_ALL.getId());
			if (studyIds.isEmpty()) {
				return new PageImpl<>();
			}
			result = solrRepository.findByStudyIdInAndDatasetIdIn(studyIds, datasetIds, pageable);
		}
		return result;
	}

}
