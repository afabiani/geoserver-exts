package org.opengeo.data.importer;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.coverage.grid.io.UnknownFormat;
import org.geotools.data.DataStoreFactorySpi;
import org.geotools.data.FileDataStoreFactorySpi;
import org.geotools.data.FileDataStoreFinder;
import org.opengeo.data.importer.format.KMLFileFormat;
import org.opengeo.data.importer.job.ProgressMonitor;
import org.vfny.geoserver.util.DataStoreUtils;

/**
 * Represents a type of data and encapsulates I/O operations.
 * 
 * @author Justin Deoliveira, OpenGeo
 *
 */

public abstract class DataFormat implements Serializable {

    /** serialVersionUID */
    private static final long serialVersionUID = 1L;

    /**
     * mappings of file name extension to format.
     */
    static Map<String,Class<? extends DataFormat>> extToFormat = 
        new HashMap<String, Class<? extends DataFormat>>();
    static {
        //extToFormat.put("shp", ShapefileFormat.class);
        extToFormat.put("kml", KMLFileFormat.class);
    }

    /**
     * looks up a format based on file extension.
     */
    public static DataFormat lookup(File file) {
        String ext = FilenameUtils.getExtension(file.getName());
        if (ext != null && extToFormat.containsKey(ext)) {
            Class<? extends DataFormat> clazz = extToFormat.get(ext);
            try {
                return clazz.newInstance();
            } 
            catch(Exception e) {}
        }

        //look for a datastore that can handle the file 
        FileDataStoreFactorySpi factory = FileDataStoreFinder.getDataStoreFactory(ext);
        if (factory != null) {
            return new DataStoreFormat(factory);
        }

        //look for a gridformat that can handle the file
        Set<AbstractGridFormat> formats = GridFormatFinder.findFormats(file);
        AbstractGridFormat format = null;
        // in the case of 2 formats, let's ensure any ambiguity that cannot
        // be resolved is an error to prevent spurious bugs related to
        // the first format that is found being returned (and this can vary
        // to to hashing in the set)
        if (formats.size() > 1) {
            for (AbstractGridFormat f: formats) {
                // prefer GeoTIFF over WorldImageFormat
                if ("GeoTIFF".equals(f.getName())) {
                    format = f;
                    break;
                }
            }
            if (format == null) {
                throw new RuntimeException("multiple formats found but not handled " + formats);
            }
        } else if (formats.size() == 1) {
            format = formats.iterator().next();
        }
        if (format != null && !(format instanceof UnknownFormat)) {
            return new GridFormat(format);
        }
        
        return null;
    }

    /**
     * Looks up a format based on a set of connection parameters. 
     */
    public static DataFormat lookup(Map<String,Serializable> params) {
        DataStoreFactorySpi factory = (DataStoreFactorySpi) DataStoreUtils.aquireFactory(params);
        if (factory != null) {
            return new DataStoreFormat(factory);
        }
        return null;
    }

    public abstract String getName();

    public abstract boolean canRead(ImportData data) throws IOException;

    public abstract StoreInfo createStore(ImportData data, WorkspaceInfo workspace, Catalog catalog) 
        throws IOException;

    public abstract List<ImportTask> list(ImportData data, Catalog catalog, ProgressMonitor monitor) 
        throws IOException;
}
