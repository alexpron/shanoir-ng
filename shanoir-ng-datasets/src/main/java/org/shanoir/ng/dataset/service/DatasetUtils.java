package org.shanoir.ng.dataset.service;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

import org.shanoir.ng.dataset.modality.*;
import org.shanoir.ng.dataset.model.Dataset;
import org.shanoir.ng.dataset.model.DatasetExpression;
import org.shanoir.ng.dataset.model.DatasetExpressionFormat;
import org.shanoir.ng.dataset.model.DatasetMetadata;
import org.shanoir.ng.dataset.model.DatasetModalityType;
import org.shanoir.ng.datasetfile.DatasetFile;

public class DatasetUtils {

	/**
	 * Reads all dataset files depending on the format attached to one dataset.
	 * @param dataset
	 * @param pathURLs
	 * @throws MalformedURLException
	 */
	public static void getDatasetFilePathURLs(final Dataset dataset, List<URL> pathURLs, DatasetExpressionFormat format) throws MalformedURLException {
		List<DatasetExpression> datasetExpressions = dataset.getDatasetExpressions();
		for (Iterator<DatasetExpression> itExpressions = datasetExpressions.iterator(); itExpressions.hasNext();) {
			DatasetExpression datasetExpression = (DatasetExpression) itExpressions.next();
			if (datasetExpression.getDatasetExpressionFormat().equals(format)) {
				List<DatasetFile> datasetFiles = datasetExpression.getDatasetFiles();
				for (Iterator<DatasetFile> itFiles = datasetFiles.iterator(); itFiles.hasNext();) {
					DatasetFile datasetFile = (DatasetFile) itFiles.next();
					URL url = new URL(datasetFile.getPath().replaceAll("%20", " "));
					pathURLs.add(url);
				}
			}
		}
	}
	
	
	public static Dataset buildDatasetFromType(String type) {
		
		DatasetMetadata originMetadata = new DatasetMetadata();
		Dataset dataset = null;
			
		switch(type) {
			case CalibrationDataset.datasetType:
				dataset = new CalibrationDataset();
				originMetadata.setDatasetModalityType(DatasetModalityType.GENERIC_DATASET);
				break;
			case CtDataset.datasetType:
				dataset = new CtDataset();
				originMetadata.setDatasetModalityType(DatasetModalityType.CT_DATASET);
				break;
			case EegDataset.datasetType:
				dataset = new EegDataset();
				originMetadata.setDatasetModalityType(DatasetModalityType.EEG_DATASET);
				break;
			case MegDataset.datasetType:
				dataset = new MegDataset();
				originMetadata.setDatasetModalityType(DatasetModalityType.EEG_DATASET);
				break;
			case MeshDataset.datasetType:
				dataset = new MeshDataset();
				originMetadata.setDatasetModalityType(DatasetModalityType.GENERIC_DATASET);
				break;
			case MrDataset.datasetType:
				dataset = new MrDataset();
				originMetadata.setDatasetModalityType(DatasetModalityType.MR_DATASET);
				break;
			case ParameterQuantificationDataset.datasetType:
				dataset = new ParameterQuantificationDataset();
				originMetadata.setDatasetModalityType(DatasetModalityType.GENERIC_DATASET);
				break;
			case PetDataset.datasetType:
				dataset = new PetDataset();
				originMetadata.setDatasetModalityType(DatasetModalityType.PET_DATASET);
				break;
			case RegistrationDataset.datasetType:
				dataset = new RegistrationDataset();
				originMetadata.setDatasetModalityType(DatasetModalityType.GENERIC_DATASET);
				break;
			case SegmentationDataset.datasetType:
				dataset = new SegmentationDataset();
				originMetadata.setDatasetModalityType(DatasetModalityType.GENERIC_DATASET);
				break;
			case SpectDataset.datasetType:
				dataset = new SpectDataset();
				originMetadata.setDatasetModalityType(DatasetModalityType.SPECT_DATASET);
				break;
			case StatisticalDataset.datasetType:
				dataset = new StatisticalDataset();
				originMetadata.setDatasetModalityType(DatasetModalityType.GENERIC_DATASET);
				break;
			case TemplateDataset.datasetType:
				dataset = new TemplateDataset();
				originMetadata.setDatasetModalityType(DatasetModalityType.GENERIC_DATASET);
				break;
			case BidsDataset.datasetType:
				dataset = new BidsDataset();
				originMetadata.setDatasetModalityType(DatasetModalityType.MR_DATASET);
				break;
			case XaDataset.datasetType:
				dataset = new XaDataset();
				originMetadata.setDatasetModalityType(DatasetModalityType.XA_DATASET);
				break;
			default:
				dataset = new GenericDataset();
				originMetadata.setDatasetModalityType(DatasetModalityType.GENERIC_DATASET);
				break;
		}
		dataset.setOriginMetadata(originMetadata);
		return dataset;
	}

	public static Dataset copyDatasetFromDataset(Dataset d) {
		String type = d.getType();
		Dataset dataset = null;

		switch(type) {
			case CalibrationDataset.datasetType:
				dataset = new CalibrationDataset(d);
				break;
			case CtDataset.datasetType:
				dataset = new CtDataset(d);
				break;
			case EegDataset.datasetType:
				dataset = new EegDataset(d);
				break;
			case MegDataset.datasetType:
				dataset = new MegDataset(d);
				break;
			case MeshDataset.datasetType:
				dataset = new MeshDataset(d);
				break;
			case ParameterQuantificationDataset.datasetType:
				dataset = new ParameterQuantificationDataset(d);
				break;
			case PetDataset.datasetType:
				dataset = new PetDataset(d);
				break;
			case RegistrationDataset.datasetType:
				dataset = new RegistrationDataset(d);
				break;
			case SegmentationDataset.datasetType:
				dataset = new SegmentationDataset(d);
				break;
			case SpectDataset.datasetType:
				dataset = new SpectDataset(d);
				break;
			case StatisticalDataset.datasetType:
				dataset = new StatisticalDataset(d);
				break;
			case TemplateDataset.datasetType:
				dataset = new TemplateDataset(d);
				break;
			case BidsDataset.datasetType:
				dataset = new BidsDataset(d);
				break;
			case XaDataset.datasetType:
				dataset = new XaDataset(d);
				break;
			default:
				dataset = new GenericDataset(d);
				break;
		}
		return dataset;
	}
	
}
