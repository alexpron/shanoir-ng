ALTER TABLE subject_study ADD COLUMN subject_type int(11) DEFAULT NULL;

UPDATE datasets.subject_study JOIN studies.subject_study ON datasets.subject_study.id = studies.subject_study.id SET datasets.subject_study.subject_type = studies.subject_study.subject_type;

INSERT INTO datasets.acquisition_equipment (id, name) SELECT smm.id AS id, CONCAT(sm.name, ' ', smm.name) AS name FROM studies.manufacturer_model AS smm INNER JOIN studies.manufacturer AS sm ON smm.manufacturer_id = sm.id;