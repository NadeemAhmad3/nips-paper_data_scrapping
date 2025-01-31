package df;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

public class datafetching {
    private static final String BASE_URL = "https://papers.nips.cc/";
    private static final String DOWNLOAD_PATH = "D:\\ds";

    private static JTable table;
    private static DefaultTableModel tableModel;
    private static Map<String, FolderData> folderDataMap = new HashMap<>();

    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:90.0) Gecko/20100101 Firefox/90.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.159 Safari/537.36",
        "Mozilla/5.0 (Linux; Android 11; Pixel 4 XL) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/93.0.4577.63 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.81 Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:92.0) Gecko/20100101 Firefox/92.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.81 Safari/537.36",
        "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Mobile Safari/537.36",
        "Mozilla/5.0 (Windows NT 6.1; WOW64; Trident/7.0; AS; rv:11.0) like Gecko",
        "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:91.0) Gecko/20100101 Firefox/91.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:96.0) Gecko/20100101 Firefox/96.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.1 Safari/605.1.15",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:95.0) Gecko/20100101 Firefox/95.0",
        "Mozilla/5.0 (iPad; CPU OS 15_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1"
    };

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> createAndShowGUI());

        // Use a thread pool for parallel processing
        ExecutorService executor = Executors.newFixedThreadPool(10); // Adjust pool size based on your system

        try {
            String userAgent = USER_AGENTS[new Random().nextInt(USER_AGENTS.length)];
            Document document = Jsoup.connect(BASE_URL)
                                    .userAgent(userAgent)
                                    .timeout(5000)
                                    .get();

            Elements links = document.select("li > a");
            for (Element link : links) {
                String href = link.attr("href");
                if ("/admin/login/?next=/admin/".equals(href) || "/admin/logout/?nextp=/admin".equals(href)) {
                    continue;
                }

                String folderName = href.replaceFirst("/paper_files/paper/", "");
                String paperFolderPath = DOWNLOAD_PATH + "/" + folderName;
                Files.createDirectories(Paths.get(paperFolderPath));

                // Submit tasks to the thread pool
                executor.submit(() -> processHref(BASE_URL + href, userAgent, paperFolderPath, folderName));
            }
        } catch (IOException e) {
            System.err.println("Error fetching data: " + e.getMessage());
        } finally {
            executor.shutdown(); // Shutdown the executor after all tasks are submitted
            try {
                executor.awaitTermination(1, TimeUnit.HOURS); // Wait for all tasks to complete
            } catch (InterruptedException e) {
                System.err.println("Executor shutdown interrupted: " + e.getMessage());
            }
        }
    }

    private static void processHref(String url, String userAgent, String folderPath, String foldername) {
        folderDataMap.put(foldername, new FolderData(foldername));
        updateTable(foldername);

        try {
            Document document = Jsoup.connect(url)
                                    .userAgent(userAgent)
                                    .timeout(5000)
                                    .get();

            Elements links = document.select("li > a");
            int totalLinks = links.size();
            folderDataMap.get(foldername).setTotalLinks(totalLinks);
            updateTable(foldername);

            for (Element link : links) {
                String href = link.attr("href");
                String modifiedUrl = href.replaceAll("/paper_files/paper/\\d+", "");
                processAbstractContent(url + modifiedUrl, userAgent, folderPath, foldername);
            }
        } catch (IOException e) {
            System.err.println("Error processing URL: " + url + " | " + e.getMessage());
        }
    }

    private static void processAbstractContent(String url, String userAgent, String folderPath, String folderName) {
        try {
            Document document = Jsoup.connect(url)
                                    .userAgent(userAgent)
                                    .timeout(5000)
                                    .get();

            Element pdfLink = document.selectFirst("a.btn.btn-light.btn-spacer");
            if (pdfLink != null) {
                String pdfHref = pdfLink.attr("href");
                String infoUrl = BASE_URL + pdfHref;
                downloadBibAndExtractPdf(infoUrl, userAgent, folderPath, folderName);
            }
        } catch (IOException e) {
            System.err.println("Error processing URL: " + url + " | " + e.getMessage());
        }
    }

    private static void downloadBibAndExtractPdf(String bibUrl, String userAgent, String mainFolderPath, String foldername) {
        try {
            Document document = Jsoup.connect(bibUrl)
                                    .userAgent(userAgent)
                                    .timeout(5000)
                                    .ignoreContentType(true)
                                    .get();

            String bibText = document.text();

            String title = extractBibField(bibText, "title");
            String author = extractBibField(bibText, "author");
            String booktitle = extractBibField(bibText, "booktitle");

            String sanitizedTitle = sanitizeFileName(title);
            String sanitizedBooktitle = sanitizeFileName(booktitle);
            String sanitizedAuthor = sanitizeFileName(author);

            String folderName = (sanitizedBooktitle + "_" + sanitizedAuthor).replaceAll("[^a-zA-Z0-9_]", "_");
            String folderPath = mainFolderPath + "\\" + folderName + "\\";

            Files.createDirectories(Paths.get(folderPath));

            String bibFilePath = folderPath + sanitizedTitle + ".bib";
            Files.write(Paths.get(bibFilePath), bibText.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("BibTeX file saved: " + bibFilePath);

            String pdfUrl = extractPdfUrl(bibText);
            if (pdfUrl != null) {
                String fileName = sanitizedBooktitle + ".pdf";
                String filePath = folderPath + fileName;
                downloadPdf(pdfUrl, filePath);

                folderDataMap.get(foldername).incrementDownloadedPDFs();
                updateTable(foldername);
            } else {
                System.err.println("PDF URL not found in .bib file: " + bibUrl);
            }
        } catch (IOException e) {
            System.err.println("Error downloading .bib file: " + bibUrl + " | " + e.getMessage());
        }
    }

    private static String extractBibField(String bibText, String fieldName) {
        String pattern = fieldName + "\\s*=\\s*\\{([^{}]+)\\}";
        java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher matcher = regex.matcher(bibText);

        String lastMatch = null;
        while (matcher.find()) {
            lastMatch = matcher.group(1).trim();
        }

        return lastMatch != null ? lastMatch : "Unknown_" + fieldName;
    }

    private static String extractPdfUrl(String bibText) {
        String urlPattern = "url\\s*=\\s*\\{(https?://[^\"]+\\.pdf)\\}";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(urlPattern);
        java.util.regex.Matcher matcher = pattern.matcher(bibText);

        return matcher.find() ? matcher.group(1) : null;
    }

    private static void downloadPdf(String pdfUrl, String filePath) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try (InputStream in = new BufferedInputStream(new URL(pdfUrl).openStream())) {
                Files.copy(in, Paths.get(filePath), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Downloaded: " + filePath);
                return;
            } catch (IOException e) {
                System.err.println("Retry " + (i + 1) + "/" + maxRetries + " failed: " + pdfUrl);
                if (i == maxRetries - 1) {
                    System.err.println("Download failed after " + maxRetries + " attempts: " + pdfUrl);
                }
            }
        }
    }

    private static String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Data Fetching Progress");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 400);

        String[] columnNames = {"Folder Name", "Total Links", "Downloaded PDFs", "Progress"};
        tableModel = new DefaultTableModel(columnNames, 0);
        table = new JTable(tableModel);

        table.getColumnModel().getColumn(3).setCellRenderer(new ProgressCellRenderer());

        JScrollPane scrollPane = new JScrollPane(table);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private static void updateTable(String folderName) {
        SwingUtilities.invokeLater(() -> {
            FolderData folderData = folderDataMap.get(folderName);
            int rowIndex = -1;

            for (int i = 0; i < tableModel.getRowCount(); i++) {
                if (tableModel.getValueAt(i, 0).equals(folderName)) {
                    rowIndex = i;
                    break;
                }
            }

            double progress = 0;
            if (folderData.getTotalLinks() > 0) {
                progress = (double) folderData.getDownloadedPDFs() / folderData.getTotalLinks() * 100;
            }

            if (rowIndex == -1) {
                tableModel.addRow(new Object[]{folderName, folderData.getTotalLinks(), folderData.getDownloadedPDFs(), progress});
            } else {
                tableModel.setValueAt(folderData.getTotalLinks(), rowIndex, 1);
                tableModel.setValueAt(folderData.getDownloadedPDFs(), rowIndex, 2);
                tableModel.setValueAt(progress, rowIndex, 3);
            }
        });
    }

    private static class ProgressCellRenderer extends JLabel implements TableCellRenderer {
        public ProgressCellRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            double progress = (double) value;
            setText(String.format("%.2f%%", progress));

            if (progress < 5) {
                setBackground(Color.RED);
            } else if (progress < 10) {
                setBackground(Color.YELLOW);
            } else {
                setBackground(Color.GREEN);
            }

            return this;
        }
    }

    private static class FolderData {
        private String folderName;
        private int totalLinks;
        private int downloadedPDFs;

        public FolderData(String folderName) {
            this.folderName = folderName;
            this.totalLinks = 0;
            this.downloadedPDFs = 0;
        }

        public String getFolderName() {
            return folderName;
        }

        public int getTotalLinks() {
            return totalLinks;
        }

        public void setTotalLinks(int totalLinks) {
            this.totalLinks = totalLinks;
        }

        public int getDownloadedPDFs() {
            return downloadedPDFs;
        }

        public void incrementDownloadedPDFs() {
            this.downloadedPDFs++;
        }
    }
}
