package org.shanoir.uploader.dicom.query;

import java.util.Comparator;
import java.util.Map.Entry;

import org.shanoir.uploader.dicom.DicomTreeNode;

/**
 * Dicom Study comparator based on their study date.
 *
 * @author grenard
 * @author mkain
 *
 */
public class StudyComparator implements Comparator<Entry<String, DicomTreeNode>> {

	/* (non-Javadoc)
	 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
	 */
	public int compare(Entry<String, DicomTreeNode> study1, Entry<String, DicomTreeNode> study2) {
		final String date1 = ((StudyTreeNode) study1.getValue()).getStudyDate().toString();
		final String date2 = ((StudyTreeNode) study2.getValue()).getStudyDate().toString();
		if (date1 != null && !date1.equals("")) {
			if (date2 != null && !date2.equals("")) {
				return date1.compareTo(date2);
			} else {
				return -1;
			}
		} else {
			if (date2 != null && !date2.equals("")) {
				return 1;
			} else {
				return 0;
			}
		}
	}

}
