package handlers;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Stack;

import interfaces.Chunk;

public class MdrHandler implements Runnable {
	private int PEER_ID;
	private Queue<String> msgQueue = new LinkedList<String>();
	private Stack<Chunk> chunksRequests = new Stack<Chunk>();

	public MdrHandler(Queue<String> msgQueue, int id) {
		this.msgQueue = msgQueue;
		PEER_ID = id;
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()){
			analyseMessages();
		}
	}

	/**
	 * @return the chunksReceived
	 */
	public Stack<Chunk> getRequests() {
		return chunksRequests;
	}

	/**
	 * @return the endOfFile
	 */
	public boolean isEndOfFile() {
		if(chunksRequests.isEmpty())
			return false;
		return chunksRequests.peek().isEndOfFile();
	}

	/**
	 * Analisa todas as mensagens armazenadas e cria o ficheiro se recebeu o �ltimo chunk do ficheiro.
	 */
	private void analyseMessages(){
		if (!msgQueue.isEmpty()) {
			String[] msg = msgQueue.poll().split("\\s",8);

			if(checkValidMessageType(msg[0])){
				print(msg);

				analyseHeader(msg);

				byte[] body = analyseBody(msg[7]);
				System.out.println("body size: " + body.length);
				Chunk chunk = new Chunk(msg[3], Integer.parseInt(msg[4]), body);

				chunksRequests.push(chunk);

				System.out.println("tamanho da lista de chunks recebido no mdr: " + chunksRequests.size());		

				if(chunk.isEndOfFile()){
					createFile(msg[3]);
				}
			}
		}
	}

	/**
	 * Verifica se a mensagem recebida � v�lida.
	 * A primeira verifica��o � se o MessageType est� correto.
	 * @param messageType MessageType recebido
	 * @return True se tem o MessageType correto
	 */
	private boolean checkValidMessageType(String messageType){
		return "CHUNK".equals(messageType);
	}

	/**
	 * Analisa o cabe�alho da mensagem.
	 * TODO: Encripta��o do FileId
	 * @param msg Mensagem recebida
	 * @return true se o cabe�alho � v�lido
	 */
	private boolean analyseHeader(String[] msg){
		return "1.0".equals(msg[1]) 
				&& "0xD0xA".equals(msg[5]) 
				&& "0xD0xA".equals(msg[6]);
	}

	/**
	 * Analisa o body da mensagem.
	 * Converte o body de string para byte[].
	 * TODO: N�o sei se � �til.
	 * @param msg em string
	 * @return body como byte[]
	 */
	private byte[] analyseBody(String msg){
		byte[] destination = null;
		destination = msg.getBytes();
		return destination;
	}

//	/**
//	 * Junta dois byte arrays.
//	 * @param first array para colocar no inicio do novo array
//	 * @param second array para colocar no fim do novo array
//	 * @return Array = first + second
//	 */
//	private byte[] joinArrays(byte[] first, byte[] second){
//		byte[] destination = new byte[first.length + second.length];
//
//		System.arraycopy(first, 0, destination, 0, first.length);
//		System.arraycopy(second, 0, destination, first.length, second.length);
//
//		return destination;
//	}

	private void createFile(String fileId) {
//		byte[] data = new byte[0];
//		Path path = Paths.get(("./files/" + fileId));
//
//		while(!chunksRequests.isEmpty()){
//			Chunk c = chunksRequests.pop();
//			if(c.getFileId().equals(fileId))
//				data = joinArrays(c.getContent(), data);
//		}		
//
//		try {
//			Files.createDirectories(path.getParent());
//			Files.createFile(path);
//			Files.write(path, data, StandardOpenOption.APPEND);
//		} catch (FileAlreadyExistsException e) {
//			System.err.println("File already exists: " + e.getMessage());
//		} catch (IOException e) {
//			System.err.println("I/O error in mdrHandler createFile.");
//		}
		
		try {
			mergeFiles("./chunks/" + fileId, "./files/" + fileId);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	public static void mergeFiles(List<File> files, File into)
			throws IOException {
		try (BufferedOutputStream mergingStream = new BufferedOutputStream(
				new FileOutputStream(into))) {
			for (File f : files) {
				Files.copy(f.toPath(), mergingStream);
			}
		}
	}

	public static List<File> listOfFilesToMerge(File oneOfFiles) {
		String tmpName = oneOfFiles.getName();//{name}.{number}
		String destFileName = tmpName.substring(0, tmpName.lastIndexOf('.'));//remove .{number}
		File[] files = oneOfFiles.getParentFile().listFiles(
				(File dir, String name) -> name.matches(destFileName + "[.]\\d+"));
		Arrays.sort(files);//ensuring order 001, 002, ..., 010, ...
		return Arrays.asList(files);
	}

	public static void mergeFiles(File oneOfFiles, File into)
			throws IOException {
		mergeFiles(listOfFilesToMerge(oneOfFiles), into);
	}

	public static List<File> listOfFilesToMerge(String oneOfFiles) {
		return listOfFilesToMerge(new File(oneOfFiles));
	}

	public static void mergeFiles(String oneOfFiles, String into) throws IOException{
		mergeFiles(new File(oneOfFiles), new File(into));
	}

	/**
	 * Imprime a mensagem no ecr�.
	 * @param msg Array de strings a ser imprimido
	 */
	protected void print(String[] msg) {
		System.out.println("\nReceived on MDR: ");
		for(int i = 0; i < msg.length-1; i++)
			System.out.print(msg[i] + "; ");
		System.out.print("<body>\n");
	}
}
