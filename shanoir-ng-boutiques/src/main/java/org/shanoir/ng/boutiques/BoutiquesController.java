package org.shanoir.ng.boutiques;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
//import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

//import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
//import javax.validation.Valid;
import javax.servlet.http.HttpServletResponse;

import org.keycloak.KeycloakPrincipal;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.representations.AccessToken;
import org.shanoir.ng.boutiques.model.BoutiquesTool;
//import org.shanoir.ng.shared.exception.TokenNotFoundException;
//import org.shanoir.ng.utils.ShanoirStudiesException;
//import org.shanoir.ng.shared.exception.ErrorModel;
//import org.shanoir.ng.shared.exception.RestServiceException;
//import org.shanoir.ng.utils.KeycloakUtil;
//import org.shanoir.ng.dataset.model.Dataset;
//import org.shanoir.ng.dataset.model.DatasetExpression;
//import org.shanoir.ng.dataset.model.DatasetExpressionFormat;
//import org.shanoir.ng.dataset.service.DatasetService;
//import org.shanoir.ng.datasetfile.DatasetFile;
//import org.shanoir.ng.download.WADODownloaderService;
//import org.shanoir.ng.shared.exception.ErrorModel;
//import org.shanoir.ng.shared.exception.RestServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
//import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

//import io.swagger.annotations.ApiParam;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
//import java.net.MalformedURLException;
//import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;

import java.io.BufferedReader;

class BoutiquesProcess {
	Process process;
	BufferedReader inputBufferedReader;
	BufferedReader errorBufferedReader;
	public BoutiquesProcess(Process process) {
		this.process = process;
		this.inputBufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        this.errorBufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
	}
}

//@CrossOrigin(origins = "http://localhost:4200")
@CrossOrigin(origins = "https://shanoir-ng-nginx")
@RestController
public class BoutiquesController {

	private static final String ZIP = ".zip";

	private static final String DOWNLOAD = ".download";

	private static final String JAVA_IO_TMPDIR = "java.io.tmpdir";
	private static final String BOUTIQUES = "boutiques";

	private static final SecureRandom RANDOM = new SecureRandom();
	
	private static final int MAX_OUTPUT_LINES = 25;
	
	public static final Map<String, BoutiquesProcess> processes = new HashMap<String, BoutiquesProcess>();

    private final HttpServletRequest request;

    @org.springframework.beans.factory.annotation.Autowired
    public BoutiquesController(HttpServletRequest request) {
        this.request = request;
    }
    
//	@Autowired
//	private DatasetService datasetService;
//	
//	@Autowired
//	private WADODownloaderService downloader;

	
	@Autowired
	private SimpMessagingTemplate brokerMessagingTemplate;
    
	private void sendMessage(String message) throws Exception {
    	this.brokerMessagingTemplate.convertAndSend("/message/messages", message);
    }
    
    private void sendError(String message) throws Exception {
    	this.brokerMessagingTemplate.convertAndSend("/message/errors", message);
    }

//    @PreAuthorize("hasRole('ADMIN') or (hasAnyRole('EXPERT', 'USER') and @importSecurityService.hasRightOnOneStudy('CAN_IMPORT'))")
    @GetMapping("/tool/search")
    public ArrayList<BoutiquesTool> searchTool(@RequestParam(value="query", defaultValue="") String query) {

        ArrayList<BoutiquesTool> searchResults = new ArrayList<BoutiquesTool>();
        try {
        	ArrayList<String> output = new ArrayList<String>();
        	BoutiquesUtils.runCommandLineSync(BoutiquesUtils.BOUTIQUES_COMMAND + " search " + query, null, output);
        	searchResults = BoutiquesUtils.parseBoutiquesSearch(output);
        	
        } catch (Exception e) {
            System.out.println("Error: " + e);
        }

        return searchResults;
    }

    @GetMapping("/tool/all")
    public ArrayList<BoutiquesTool> getAllTools() {

    	Path filePath = Paths.get(System.getProperty("user.home"), ".cache", "boutiques", "descriptors.json");
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayList<BoutiquesTool> boutiquesTools = new ArrayList<BoutiquesTool>();
        try {
	        ObjectNode descriptors = objectMapper.readValue(filePath.toFile(), ObjectNode.class);

	        Iterator<Entry<String, JsonNode>> iter = descriptors.fields();
	        while (iter.hasNext()) {
	            Entry<String, JsonNode> entry = iter.next();
	            String toolId = entry.getKey();
	            JsonNode toolDescriptor = entry.getValue();
	            String name = toolDescriptor.get("name").asText();
	            String description = toolDescriptor.get("description").asText();
	            int nDownloads = toolDescriptor.get("nDownloads").asInt();
		        boutiquesTools.add(new BoutiquesTool(toolId, name, description, nDownloads));
	        }
	        
	        return boutiquesTools;
        } catch (Exception e) {
            System.out.println("Error: " + e);
            return null;
        }
    }
    
    @GetMapping("/tool/{id}/descriptor/")
    public ObjectNode getDescriptorById(@PathVariable String id) {

        String descriptorFileName = id.replace('.', '-') + ".json";
        ObjectMapper objectMapper = new ObjectMapper();

        try {
	        File file = Paths.get(System.getProperty("user.home") , ".cache", "boutiques", descriptorFileName).toFile();
	
	        ObjectNode descriptor = objectMapper.readValue(file, ObjectNode.class);

	        return descriptor;
        } catch (Exception e) {
            System.out.println("Error: " + e);
            return null;
        }
    }
    
    @GetMapping("/tool/{id}/invocation")
    public String getInvocationById(@PathVariable String id, @RequestParam(value="complete", defaultValue="false") String completeString) {

        Boolean complete = Boolean.parseBoolean(completeString);

        try {
        	ArrayList<String> output = new ArrayList<String>();
        	BoutiquesUtils.runCommandLineSync(BoutiquesUtils.BOUTIQUES_COMMAND + " example " + (complete ? "--complete" : "") + " " + id, null, output);
        	
	        return String.join("\n", output);
        } catch (Exception e) {
            System.out.println("Error: " + e);
            return null;
        }
    }
    
    @PostMapping("/tool/{id}/generate-command/")
    public String generateCommandById(@RequestBody ObjectNode invocation, @PathVariable String id) {

        try {
        	String invocationFilePath = BoutiquesUtils.writeTemporaryFile("invocation.json", invocation.toString());
        	ArrayList<String> output = new ArrayList<String>();
        	BoutiquesUtils.runCommandLineSync(BoutiquesUtils.BOUTIQUES_COMMAND + " exec simulate -i " + invocationFilePath + " " + id, null, output);
        	
	        return String.join("\n", output);
        } catch (Exception e) {
            System.out.println("Error: " + e);
            return null;
        }
    }
    
//    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/tool/{toolId}/execute/{sessionId}")
    public String executeById(@RequestBody ObjectNode invocation, @PathVariable String toolId, @PathVariable String sessionId) {

        try {
        	// Get data from data path, use -v to mount the data path to docker container

//        	ArrayList<String> output = new ArrayList<String>();
//        	BoutiquesUtils.runCommandLineSync("docker volume inspect --format '{{.Mountpoint}}' shanoir-ng_tmp", null, output);
//        	String tmpVolume = output.get(0);
        	String tmpVolume = "tmp";
        	String tmpVolumeOnHost = System.getenv("BOUTIQUES_TMP_PATH_ON_HOST");;
        	
        	String inputPath = BoutiquesUtils.getInputPath();
        	
        	final String processId = BoutiquesUtils.getProcessId(toolId, sessionId);
        	String outputPath = BoutiquesUtils.getProcessOutputPath(processId);
//        	String outputPath = tmpVolumeOnHost + File.separator + "boutiques" + File.separator + "output";

			final File outputDir = new File(outputPath);
			if (!outputDir.exists()) {
				outputDir.mkdirs(); // create if not yet existing
			}
			
			ObjectNode descriptor = getDescriptorById(toolId);
			ArrayNode inputs = (ArrayNode) descriptor.get("inputs");

			HashMap<String, JsonNode> idToInputObject = new HashMap<String, JsonNode>(); 
	        for (int i = 0; i < inputs.size(); i++) {
	            JsonNode input = inputs.get(i);
	            String id = input.get("id").asText();
	            idToInputObject.put(id, input);
	            if(input.has("type") && input.get("type").asText().contentEquals("File") && invocation.has(id)) {
	            	String filePath = invocation.get(id).asText();
	            	invocation.put(id, inputPath + File.separator + filePath);
	            }
	        }
	        
		    // See the description of how the output files are generated: "Boutiques: a flexible framework for automatedapplication integration in computing platforms"
		    //                                                            https://arxiv.org/pdf/1711.09713.pdf

		    // For all inputs: if the parameter is a File or a String and has a "value-key":
		    //      check if an "output-file" has a "path-template" containing this "value-key",
		    //      remove all "path-template-stripped-extensions" from the input parameter value (which is a file name),
		    //      then replace this "value-key" in the "path-template" with the file name (= the input parameter value)
		    //      check that the resulting output paths are not absolute and do not contain double dot symbols (../)			
			for (Map.Entry<String, JsonNode> idAndInputObject : idToInputObject.entrySet()) {
		        String inputId = idAndInputObject.getKey();
		        JsonNode inputObject = idAndInputObject.getValue();
		        if(!inputObject.has("type")) {
		        	continue;
		        }
		        String inputType = inputObject.get("type").asText();
		        
		        if((inputType.contentEquals("File") || inputType.contentEquals("String")) && invocation.has(inputId)) {
		            // For all output files: check if one has "path-template" containing the "value-key" of the current input
		            String fileName = invocation.get(inputId).asText();
		            
					ArrayNode outputFiles = (ArrayNode) descriptor.get("output-files");
		            
		    	    for (int i = 0; i < outputFiles.size(); i++) {

		                JsonNode outputFilesDescription = outputFiles.get(i);
		                String pathTemplate = outputFilesDescription.get("path-template").asText();

		                // If the input is a File, remove the "path-template-stripped-extensions"
		                if(inputType.contentEquals("File") && outputFilesDescription.has("path-template-stripped-extensions")) {
		                	ArrayNode pathTemplateStrippedExtensions = (ArrayNode) outputFilesDescription.get("path-template-stripped-extensions");

		                    for (int j = 0 ; j<pathTemplateStrippedExtensions.size() ; ++j) {
		                        String pathTemplateStrippedExtension = pathTemplateStrippedExtensions.get(j).asText();
		                        fileName.replace(pathTemplateStrippedExtension, "");
		                    }
		                }

		                String valueKey = inputObject.get("value-key").asText();
		                
		                if(pathTemplate.contains(valueKey)) {
		                    // If the current output file has a "path-template" containing the current input "value-key": replace the "value-key" by the file name (!input value)
		                	pathTemplate = pathTemplate.replace(valueKey, fileName);

		                    // Make sure the path is not absolute and does not contain ../
		                    if(pathTemplate.contains("../"))
		                    {
		                        return "Error: Output paths must not contain double-dot symbols (../).";
		                    }
		                    if(pathTemplate.startsWith("/")) {
		                    	return "Error: Output paths must not be absolute.";
		                    }
		                }
		            }
		        }
		    }
        	
        	String invocationFilePath = BoutiquesUtils.writeTemporaryFile("invocation.json", invocation.toString());
        	
        	String command = BoutiquesUtils.BOUTIQUES_COMMAND + " exec launch -s " + toolId + " " + invocationFilePath + " -v " + tmpVolumeOnHost + ":/tmp";
        	System.out.println(command);
        	BoutiquesProcess boutiquesProcess = processes.get(processId); 
        	if(boutiquesProcess != null) {
        		boutiquesProcess.process.destroy();
        	}
        	Process process = BoutiquesUtils.runCommandLineAsync(command, outputPath);
        	processes.put(processId, new BoutiquesProcess(process));
        	BoutiquesUtils.sendProcessStreams(process, (message, isError)-> {
        		try {
            		if(isError) {
            			this.sendError(message);
            		} else {
                		this.sendMessage(message);	
            		}
        		} catch (Exception ex) {
        	        ex.printStackTrace();
        	    }
        	});

	        return "Execution started...";
        } catch (Exception e) {
            System.out.println("Error: " + e);
            return "Error: " + e;
        }
    }

	/**
	 * Zip
	 * 
	 * @param sourceDirPath
	 * @param zipFilePath
	 * @throws IOException
	 */
	private void zip(String sourceDirPath, String zipFilePath) throws IOException {
		Path p = Paths.get(zipFilePath);
		try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(p))) {
			Path pp = Paths.get(sourceDirPath);
			Files.walk(pp)
				.filter(path -> !Files.isDirectory(path))
				.forEach(path -> {
					ZipEntry zipEntry = new ZipEntry(pp.relativize(path).toString());
					try {
						zos.putNextEntry(zipEntry);
						Files.copy(path, zos);
						zos.closeEntry();
					} catch (IOException e) {
//						LOG.error(e.getMessage(), e);
					}
				});
            	zos.finish();
            zos.close();
		}
	}
	
    private String zipOutput(String processId) throws IOException {
    	String outputPath = BoutiquesUtils.getProcessOutputPath(processId);
    	String outputPathZip = outputPath + ".zip";
    	File file = new File(outputPathZip);
    	file.deleteOnExit();
    	zip(outputPath, outputPathZip);
    	return outputPath + ".zip";
    }
    
    @GetMapping("/tool/{toolId}/output/{sessionId}")
    public ObjectNode getExecutionOutputById(@PathVariable String toolId, @PathVariable String sessionId) {

    	final String processId = BoutiquesUtils.getProcessId(toolId, sessionId);
    	
    	BoutiquesProcess boutiquesProcess = processes.get(processId);
    	if(boutiquesProcess != null) {
			ObjectMapper objectMapper = new ObjectMapper();
			ObjectNode results = objectMapper.createObjectNode();
			
    		try {
    			ArrayNode inputLines = objectMapper.createArrayNode();
    			ArrayNode errorLines = objectMapper.createArrayNode();
    			boolean isAlive = boutiquesProcess.process.isAlive();
    			
//    			// Not really blocking version (but the first readLine seems to block):
//    			String inputLine = boutiquesProcess.inputBufferedReader.readLine();
//    			String errorLine = boutiquesProcess.errorBufferedReader.readLine();
//    			
//    			if(inputLine != null) {
//    				inputLines.add(inputLine);    				
//    			}
//    			if(errorLine != null) {
//    				errorLines.add(errorLine);    				
//    			}

    			// Blocking version
    			String inputLine = null;
    			String errorLine = null;
    			while((isAlive && inputLines.size() == 0 || !isAlive && inputLines.size() < MAX_OUTPUT_LINES) && (inputLine = boutiquesProcess.inputBufferedReader.readLine()) != null) {
    				inputLines.add(inputLine);
    			}
    			while((isAlive && errorLines.size() == 0 || !isAlive && errorLines.size() < MAX_OUTPUT_LINES) && (errorLine = boutiquesProcess.errorBufferedReader.readLine()) != null) {
    				errorLines.add(errorLine);
    			}
    			
    			boolean finished = !isAlive && inputLine == null && errorLine == null;
    			if(finished) {
    				processes.remove(processId);
    			}
    			
    			results.set("input", inputLines);
    			results.set("error", errorLines);
    			results.put("finished", finished);
    	    } catch (IOException ex) {
                System.out.println("Server error: " + ex.getMessage());
    			results.put("server error", "Server error while executing process.");
    	    }
			return results;
    	}
    	return null;
    }
    
    @GetMapping("/tool/{toolId}/download-output/{sessionId}")
    public ResponseEntity<ByteArrayResource> downloadOutputById(@PathVariable String toolId, @PathVariable String sessionId) throws ResponseStatusException {

		try {
	    	final String processId = BoutiquesUtils.getProcessId(toolId, sessionId);
	    	File zipFile = new File(zipOutput(processId));

			byte[] data = Files.readAllBytes(zipFile.toPath());
			ByteArrayResource resource = new ByteArrayResource(data);
	
			// Try to determine file's content type
			String contentType = request.getServletContext().getMimeType(zipFile.getAbsolutePath());

			return ResponseEntity.ok()
					.header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + zipFile.getName())
					.contentType(MediaType.parseMediaType(contentType))
					.contentLength(data.length)
					.body(resource);

		} catch (IOException e) {
			e.printStackTrace();
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Error while creating zip file.", e);
		}
    }
    
   public Map<String, Object> listDirectoryTree( File dir ) {
   	File[] content = dir.listFiles(); 

   	Map<String, Object> files = new HashMap<String, Object>();

   	for( File f : content ) {
			Map<String, Object> subList = listDirectoryTree( f );
   		files.put( f.getName(), subList );
   	}

   	return files;
   }

   public List<Map<String, Object>> listDirectoryTreeComplete( File dir ) {
   	File[] content = dir.listFiles(); 

   	List<Map<String,Object>> files = new ArrayList<Map<String, Object>>();

   	for( File f : content ) {
   		HashMap<String, Object> fileObject = new HashMap<String, Object>();
   		fileObject.put("name", f.getName());
   		fileObject.put("path", f.getAbsolutePath());
   		fileObject.put("isDirectory", f.isDirectory());
   		if(f.isDirectory()) {
       		fileObject.put("files", listDirectoryTreeComplete( f ));
   		}
   		files.add(fileObject);
   	}

   	return files;
   }

   public ArrayNode listDirectoryObjectNode( File dir ) {
   	File[] content = dir.listFiles(); 
   	
   	ObjectMapper mapper = new ObjectMapper();
       ArrayNode files = mapper.createArrayNode();

   	for( File f : content ) {
   		ObjectNode fileObject = mapper.createObjectNode();
   		fileObject.put("name", f.getName());
   		fileObject.put("path", f.getAbsolutePath());
   		fileObject.put("isDirectory", f.isDirectory());
   		if(f.isDirectory()) {
       		fileObject.set("files", listDirectoryObjectNode( f ));
   		}
   		files.add(fileObject);
   	}

   	return files;
   }
   
    @PostMapping("/tool/update-database/")
    public String updateDatabase() {
    	BoutiquesUtils.updateToolDatabase();
        return "Database update started.";
    }
}
