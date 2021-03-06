package handlers;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;

import interfaces.Chunk;
import peer.Peer;

public class McHandler extends Handler implements Runnable {
	private String messageType = "";
	private Queue<Chunk> chunksToSend = new LinkedList<Chunk>();
	private Queue<Chunk> chunksToRetransmit = new LinkedList<Chunk>();
	private Map<ChunkInfo, ArrayList<Integer>> storedMap = new HashMap<ChunkInfo, ArrayList<Integer>>();

	public McHandler(Queue<byte[]> msgQueue, int id) {
		super(msgQueue, id);
	}

	/**
	 * Adiciona a mensagem de STORED � tree de mensagens Stored j� recebidas.
	 * 
	 * @param ci
	 *            Mensagem recebida
	 * @param senderId
	 *            Peer que enviou a mensagem Stored
	 */
	private void addStoredChunk(ChunkInfo ci, int senderId) {
		ArrayList<Integer> peersComChunk = new ArrayList<Integer>();

		if (storedMap.containsKey(ci)) {
			peersComChunk = storedMap.get(ci);
			if (!peersComChunk.contains(senderId)) {
				peersComChunk.add(senderId);
				storedMap.put(ci, peersComChunk);
			}
		} else {
			peersComChunk.add(senderId);
			storedMap.put(ci, peersComChunk);
		}
	}

	/**
	 * Analisa o cabe�alho da mensagem.
	 * 
	 * @param msg
	 *            Mensagem recebida
	 * @return true se o cabe�alho � v�lido
	 */
	private boolean analyseHeader(String[] msg) {
		return "1.0".equals(msg[1]) && PEER_ID != Integer.parseInt(msg[2]);
	}

	/**
	 * Analisa todas as mensagens armazenadas vindas do MC.
	 */
	private void analyseMessages() {
		if (!msgQueue.isEmpty()) {
			byte[] data = msgQueue.poll();
			String convert = new String(data, 0, data.length);
			String[] msg = convert.substring(0, convert.indexOf("\r\n")).split("\\s");

			if (checkMessageType(msg[0]) && analyseHeader(msg)) {
				print(msg);
				if ("STORED".equals(this.messageType)) {
					ChunkInfo chunkinfo = new ChunkInfo(msg[3], Integer.parseInt(msg[4]));
					addStoredChunk(chunkinfo, Integer.parseInt(msg[2]));
				} else if ("DELETE".equals(this.messageType)) {
					deleteFiles(msg[3]);
				} else if ("GETCHUNK".equals(this.messageType)) {
					searchChunk(data);
				} else if ("REMOVED".equals(this.messageType)) {
					int senderId = Integer.parseInt(msg[2]);
					doIHave(msg[3], msg[4], senderId);
				}
			}
		}
	}

	/**
	 * Verifica se o tipo de mensagem recebida � v�lida.
	 * 
	 * @param messageType
	 *            Tipo de mensagem
	 * @return true se � um tipo aceitavel, false caso contr�rio
	 */
	private boolean checkMessageType(String messageType) {
		if ("STORED".equals(messageType) || "DELETE".equals(messageType) || "GETCHUNK".equals(messageType)
				|| "REMOVED".equals(messageType))
			this.messageType = messageType;

		return !this.messageType.isEmpty();
	}

	/**
	 * Apaga todos os ficheiros come�ados por fileID
	 * 
	 * @param fileID
	 *            prefixo do nome do ficheiro a apagar
	 */
	private void deleteFiles(String fileId) {
		Path path = Paths.get("./chunks/");

		try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(path, encrypt(fileId) + "*")) {
			for (final Path file : directoryStream) {
				Files.delete(file);
			}
		} catch (IOException e) {
			System.err.println("Failed to delete chunks on MCHandler.");
		}
	}

	private void doIHave(String fileId, String chunkNo, int senderId) {
		int i = Integer.parseInt(chunkNo);
		String chunkNoStr = String.format("%03d", i);

		if (fileExists("chunks/" + fileId + "." + chunkNoStr)) {
			Path path = Paths.get("chunks/" + fileId + "." + chunkNoStr);

			try {
				Chunk c = new Chunk(fileId, Integer.parseInt(chunkNo), Files.readAllBytes(path));
				chunksToRetransmit.add(c);
				Peer.sendRetransmission(senderId);
			} catch (IOException e) {
				System.err.println("Failed to read in mcHandler.doIHave().");
			}
		}
	}

	private boolean fileExists(String path) {
		File f = new File(path);

		return f.exists() && !f.isDirectory();
	}

	public Queue<Chunk> getChunksToSend() {
		return chunksToSend;
	}

	public Queue<Chunk> getChunksToRetransmit() {
		return chunksToRetransmit;
	}

	public boolean receivedAllStored(Chunk c) {
		ChunkInfo ci = new ChunkInfo(c.getFileId(), c.getChunkNumber());
		List<Integer> li = storedMap.get(ci);

		if (li == null)
			return false;
		return c.getReplicationDegree() <= li.size();
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			analyseMessages();
		}
	}

	/**
	 * Procura o chunk do ficheiro fileId com o n�mero chunkNo na pasta dos
	 * chunks. Se encontrar adiciona ao chunksToSend para depois ser enviado.
	 * 
	 * @param fileId
	 *            Ficheiro procurado
	 * @param chunkNo
	 *            N�mero do chunk procurado
	 */
	private void searchChunk(byte[] data) {
		String convert = new String(data, 0, data.length);
		String[] msg = convert.substring(0, convert.indexOf("\r\n")).split("\\s");
		String fileId = msg[3];
		String encriptedFileId = encrypt(fileId);
		int chunkNo = Integer.parseInt(msg[4]);
		String chunkNoStr = String.format("%03d", chunkNo);

		if (fileExists("chunks/" + encriptedFileId + "." + chunkNoStr)) {
			Path path = Paths.get("chunks/" + encriptedFileId + "." + chunkNoStr);

			pauseThread();

			if (!msgQueue.contains(data))
				try {
					Chunk c = new Chunk(fileId, chunkNo, Files.readAllBytes(path));
					chunksToSend.add(c);
					Peer.sendChunks();
				} catch (IOException e) {
					System.err.println("Failed to read in mcHandler.searchChunk().");
				}
		}
	}

	/**
	 * Waits from 0 to 400ms.
	 */
	private void pauseThread() {
		try {
			new Thread(() -> {
				try {
					Thread.sleep(ThreadLocalRandom.current().nextLong(400));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}) {
				{
					start();
				}
			}.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Imprime a mensagem no ecr�.
	 * 
	 * @param msg
	 *            Array de strings a ser imprimido
	 */
	protected void print(String[] msg) {
		System.out.println("\nReceived on MC: ");
		for (int i = 0; i < msg.length; i++)
			System.out.print(msg[i] + "; ");
		System.out.println();
	}
}
