package io.mark.pmpoh.ui;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.mark.pmpoh.PimpMyPohPlugin;
import io.mark.pmpoh.objects.ObjectManager;
import io.mark.pmpoh.objects.ObjectType;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.config.ConfigPlugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Panel for displaying and selecting objects to place
 */
@Slf4j
public class ObjectsListPanel extends JPanel {
    
    private static final int ENTRIES_PER_PAGE = 100;
    private static final int MAX_RECENT_OBJECTS = 50;
    private static final String SAVE_DIR = "pimp-my-poh";
    private static final String RECENT_OBJECTS_FILE = "recent-objects.json";
    private static final String FAVORITES_OBJECTS_FILE = "favorites-objects.json";
    
    private final ObjectManager objectManager;
    private final PimpMyPohPlugin plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private int currentPage = 0;
    private JLabel pageLabel;
    private JTextField pageInput;
    private JScrollPane scrollPane;
    private JPanel objectsContainer;
    private ObjectListSlot selectedSlot;
    private IconTextField searchBar;
    private JComboBox<String> searchModeDropdown;
    private JComboBox<String> filterDropdown;
    private JButton allTabButton;
    private JButton recentTabButton;
    private JButton favoritesTabButton;
    private String searchQuery = "";
    private String searchMode = "gameval"; // "gameval", "name", or "id"
    private String currentTab = "all"; // "all", "recent", or "favorites"
    private final LinkedHashSet<String> recentObjects = new LinkedHashSet<>(); // Maintains insertion order, no duplicates
    private final LinkedHashSet<String> favoriteObjects = new LinkedHashSet<>(); // Maintains insertion order, no duplicates
    private ImageIcon favoriteIcon; // Icon to show next to favorited items
    
    @Inject
    public ObjectsListPanel(ObjectManager objectManager, PimpMyPohPlugin plugin) {
        this.objectManager = objectManager;
        this.plugin = plugin;
        
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // Load recent objects and favorites from disk
        loadRecentObjects();
        loadFavorites();
        
        // Load favorite icon
        loadFavoriteIcon();
        
        initialize();
    }
    
    private void loadFavoriteIcon() {
        try {
            java.awt.image.BufferedImage iconImage = ImageUtil.loadImageResource(ConfigPlugin.class, "star_on.png");
            if (iconImage != null) {
                favoriteIcon = new ImageIcon(iconImage);
                log.debug("Loaded favorite icon from resources");
            } else {
                log.warn("Favorite icon not found in resources");
            }
        } catch (Exception e) {
            log.warn("Failed to load favorite icon", e);
        }
    }
    
    private void initialize() {
        // Top bar
        JPanel topPanel = createTopPanel();
        
        // Bottom pagination
        JPanel bottomPanel = createBottomPanel();
        
        // Create tab buttons above the list
        JPanel tabsPanel = createTabsPanel();
        
        // Create scrollable objects list panel
        objectsContainer = new JPanel();
        objectsContainer.setLayout(new BoxLayout(objectsContainer, BoxLayout.Y_AXIS));
        objectsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        objectsContainer.setBorder(new EmptyBorder(5, 5, 5, 5));

        scrollPane = new JScrollPane(objectsContainer);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // Create a panel to hold tabs and scroll pane
        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        listPanel.add(tabsPanel, BorderLayout.NORTH);
        listPanel.add(scrollPane, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(listPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // Initial page load
        updatePage();
    }
    
    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        topPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        JLabel placeModeLabel = new JLabel("Place Mode");
        placeModeLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        
        // Search bar
        searchBar = new IconTextField();
        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setPreferredSize(new Dimension(100, 30));
        searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        
        // Add document filter to restrict ID search to numbers only
        ((AbstractDocument) searchBar.getDocument()).setDocumentFilter(new DocumentFilter()
        {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException
            {
                if (!"id".equals(searchMode) || isNumeric(string))
                {
                    super.insertString(fb, offset, string, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException
            {
                if (!"id".equals(searchMode) || isNumeric(text))
                {
                    super.replace(fb, offset, length, text, attrs);
                }
            }

            private boolean isNumeric(String str)
            {
                if (str == null || str.isEmpty())
                {
                    return true; // Allow empty string
                }
                return str.matches("[0-9]+");
            }
        });
        
        searchBar.addActionListener(e -> updatePage());
        searchBar.addClearListener(() -> {
            searchQuery = "";
            currentPage = 0;
            updatePage();
        });
        
        // Real-time search as user types
        searchBar.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                updateSearch();
            }

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                updateSearch();
            }

            @Override
            public void changedUpdate(DocumentEvent e)
            {
                updateSearch();
            }

            private void updateSearch() {
                String text = searchBar.getText();
                searchQuery = (text != null && !text.trim().isEmpty()) ? text.toLowerCase() : "";
                currentPage = 0;
                updatePage();
            }
        });
        
        // Search mode dropdown
        String[] searchModeOptions = {"Gameval", "Name", "ID"};
        searchModeDropdown = new JComboBox<>(searchModeOptions);
        searchModeDropdown.setSelectedIndex(0); // Gameval is default
        searchModeDropdown.setPreferredSize(new Dimension(100, 30));
        searchModeDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchModeDropdown.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        searchModeDropdown.setFocusable(false);
        searchModeDropdown.addActionListener(e -> {
            String selected = (String) searchModeDropdown.getSelectedItem();
            if (selected != null) {
                searchMode = selected.toLowerCase();
                // If switching to ID mode, clean non-numeric characters
                if ("id".equals(searchMode)) {
                    String text = searchBar.getText();
                    if (text != null && !text.matches("[0-9]*")) {
                        searchBar.setText(text.replaceAll("[^0-9]", ""));
                    }
                }
                updatePage();
            }
        });
        
        // Filter dropdown
        String[] filterOptions = {"All", "GameObjects", "Walls", "Floors", "Roofs", "Doors", "Windows", "Decorations"};
        filterDropdown = new JComboBox<>(filterOptions);
        filterDropdown.setSelectedIndex(0);
        filterDropdown.setPreferredSize(new Dimension(120, 30));
        filterDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        filterDropdown.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        filterDropdown.setFocusable(false);
        filterDropdown.addActionListener(e -> {
            // Filter functionality will be implemented later
            updatePage();
        });
        
        // Search bar and mode dropdown container
        JPanel searchContainer = new JPanel(new BorderLayout(5, 0));
        searchContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        searchContainer.setOpaque(false);
        searchContainer.add(searchBar, BorderLayout.CENTER);
        searchContainer.add(searchModeDropdown, BorderLayout.EAST);
        
        // Top row: search container
        // Bottom row: filter dropdown
        JPanel controlsContainer = new JPanel(new BorderLayout(0, 5));
        controlsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        controlsContainer.setOpaque(false);
        controlsContainer.add(searchContainer, BorderLayout.CENTER);
        controlsContainer.add(filterDropdown, BorderLayout.SOUTH);
        
        topPanel.add(placeModeLabel, BorderLayout.NORTH);
        topPanel.add(controlsContainer, BorderLayout.CENTER);
        
        return topPanel;
    }
    
    private JPanel createTabsPanel() {
        JPanel tabsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tabsPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));
        
        allTabButton = new JButton("All");
        allTabButton.setFocusable(false);
        allTabButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
        allTabButton.setForeground(Color.WHITE);
        allTabButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 1, 0, 1, ColorScheme.MEDIUM_GRAY_COLOR),
            BorderFactory.createEmptyBorder(5, 15, 5, 15)
        ));
        allTabButton.addActionListener(e -> switchTab("all"));
        
        recentTabButton = new JButton("Recent");
        recentTabButton.setFocusable(false);
        recentTabButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        recentTabButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        recentTabButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 1, ColorScheme.MEDIUM_GRAY_COLOR),
            BorderFactory.createEmptyBorder(5, 15, 5, 15)
        ));
        recentTabButton.addActionListener(e -> switchTab("recent"));
        
        favoritesTabButton = new JButton("Favorites");
        favoritesTabButton.setFocusable(false);
        favoritesTabButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        favoritesTabButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        favoritesTabButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 1, ColorScheme.MEDIUM_GRAY_COLOR),
            BorderFactory.createEmptyBorder(5, 15, 5, 15)
        ));
        favoritesTabButton.addActionListener(e -> switchTab("favorites"));
        
        tabsPanel.add(allTabButton);
        tabsPanel.add(recentTabButton);
        tabsPanel.add(favoritesTabButton);
        
        return tabsPanel;
    }
    
    private void switchTab(String tab) {
        currentTab = tab;
        currentPage = 0;
        updateTabButtons();
        updatePage();
    }
    
    private void updateTabButtons() {
        // Reset all buttons
        allTabButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        allTabButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        recentTabButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        recentTabButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        favoritesTabButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        favoritesTabButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        
        // Highlight active tab
        if ("all".equals(currentTab)) {
            allTabButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
            allTabButton.setForeground(Color.WHITE);
        } else if ("recent".equals(currentTab)) {
            recentTabButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
            recentTabButton.setForeground(Color.WHITE);
        } else if ("favorites".equals(currentTab)) {
            favoritesTabButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
            favoritesTabButton.setForeground(Color.WHITE);
        }
    }
    
    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bottomPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        pageLabel = new JLabel();
        pageLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        pageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel pageInfoPanel = new JPanel(new BorderLayout());
        pageInfoPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        pageInfoPanel.add(pageLabel, BorderLayout.CENTER);

        JPanel paginationPanel = new JPanel();
        paginationPanel.setLayout(new BoxLayout(paginationPanel, BoxLayout.X_AXIS));
        paginationPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JButton prevButton = new JButton("Previous");
        prevButton.addActionListener(e -> {
            if (currentPage > 0) {
                currentPage--;
                updatePage();
            }
        });

        pageInput = new JTextField(5);
        pageInput.setHorizontalAlignment(JTextField.CENTER);
        pageInput.addActionListener(e -> {
            try {
                int page = Integer.parseInt(pageInput.getText()) - 1;
                List<Map.Entry<String, ObjectType>> allObjects = getFilteredObjects();
                int totalPages = Math.max(1, (int) Math.ceil((double) allObjects.size() / ENTRIES_PER_PAGE));
                
                if (page >= 0 && page < totalPages) {
                    currentPage = page;
                    updatePage();
                } else {
                    pageInput.setText(String.valueOf(currentPage + 1));
                }
            } catch (NumberFormatException ex) {
                pageInput.setText(String.valueOf(currentPage + 1));
            }
        });

        JButton nextButton = new JButton("Next");
        nextButton.addActionListener(e -> {
            List<Map.Entry<String, ObjectType>> allObjects = getFilteredObjects();
            int totalPages = Math.max(1, (int) Math.ceil((double) allObjects.size() / ENTRIES_PER_PAGE));
            if (currentPage < totalPages - 1) {
                currentPage++;
                updatePage();
            }
        });

        paginationPanel.add(Box.createHorizontalGlue());
        paginationPanel.add(prevButton);
        paginationPanel.add(Box.createHorizontalStrut(10));
        paginationPanel.add(pageInput);
        paginationPanel.add(Box.createHorizontalStrut(10));
        paginationPanel.add(nextButton);
        paginationPanel.add(Box.createHorizontalGlue());

        bottomPanel.add(pageInfoPanel, BorderLayout.NORTH);
        bottomPanel.add(paginationPanel, BorderLayout.SOUTH);
        
        return bottomPanel;
    }
    
    private List<Map.Entry<String, ObjectType>> getFilteredObjects() {
        List<Map.Entry<String, ObjectType>> allObjects;
        
        // Get base list based on current tab
        if ("recent".equals(currentTab)) {
            // Filter to only show recent objects
            Map<String, ObjectType> allObjectsMap = new HashMap<>();
            for (Map.Entry<String, ObjectType> entry : objectManager.getAllObjectsSorted()) {
                allObjectsMap.put(entry.getKey(), entry.getValue());
            }
            // Convert LinkedHashSet to list, maintaining order
            allObjects = recentObjects.stream()
                .filter(allObjectsMap::containsKey)
                .map(gameval -> new AbstractMap.SimpleEntry<>(gameval, allObjectsMap.get(gameval)))
                .collect(Collectors.toList());
        } else if ("favorites".equals(currentTab)) {
            // Filter to only show favorite objects
            Map<String, ObjectType> allObjectsMap = new HashMap<>();
            for (Map.Entry<String, ObjectType> entry : objectManager.getAllObjectsSorted()) {
                allObjectsMap.put(entry.getKey(), entry.getValue());
            }
            // Convert LinkedHashSet to list, maintaining order
            allObjects = favoriteObjects.stream()
                .filter(allObjectsMap::containsKey)
                .map(gameval -> new AbstractMap.SimpleEntry<>(gameval, allObjectsMap.get(gameval)))
                .collect(Collectors.toList());
            // Sort favorites by gameval for consistency
            allObjects.sort(Map.Entry.comparingByKey());
        } else {
            // "all" tab - show all objects
            allObjects = objectManager.getAllObjectsSorted();
        }
        
        // Filter by search query if present
        if (!Strings.isNullOrEmpty(searchQuery)) {
            switch (searchMode) {
                case "gameval":
                    allObjects = allObjects.stream()
                        .filter(entry -> entry.getKey().toLowerCase().contains(searchQuery))
                        .collect(Collectors.toList());
                    break;
                case "name":
                    allObjects = allObjects.stream()
                        .filter(entry -> {
                            ObjectType obj = entry.getValue();
                            return obj != null && obj.name != null && obj.name.toLowerCase().contains(searchQuery);
                        })
                        .collect(Collectors.toList());
                    break;
                case "id":
                    try {
                        int searchId = Integer.parseInt(searchQuery);
                        allObjects = allObjects.stream()
                            .filter(entry -> entry.getValue() != null && entry.getValue().getId() == searchId)
                            .collect(Collectors.toList());
                    } catch (NumberFormatException e) {
                        allObjects = Collections.emptyList();
                    }
                    break;
            }
        }
        
        return allObjects;
    }
    
    private void updatePage()
    {
        List<Map.Entry<String, ObjectType>> allObjects = getFilteredObjects();
        int totalPages = Math.max(1, (int) Math.ceil((double) allObjects.size() / ENTRIES_PER_PAGE));

        // Ensure currentPage is within valid range
        if (currentPage >= totalPages)
        {
            currentPage = Math.max(0, totalPages - 1);
        }
        
        // Update page label and input
        pageLabel.setText(String.format("Page %d of %d", currentPage + 1, totalPages));
        pageInput.setText(String.valueOf(currentPage + 1));

        // Calculate range for current page
        int startIndex = currentPage * ENTRIES_PER_PAGE;
        int endIndex = Math.min(startIndex + ENTRIES_PER_PAGE, allObjects.size());
        List<Map.Entry<String, ObjectType>> pageObjects = allObjects.isEmpty() 
                ? Collections.emptyList() 
                : allObjects.subList(startIndex, endIndex);

        // Clear existing components and reset selection
        objectsContainer.removeAll();
        selectedSlot = null;

        // Map to store slots by gameval for efficient lookup
        Map<String, ObjectListSlot> slotsByGameval = new HashMap<>();

        // Add ObjectListSlot for each object with 1 pixel gap
        for (int i = 0; i < pageObjects.size(); i++)
        {
            Map.Entry<String, ObjectType> entry = pageObjects.get(i);
            ObjectType objectType = entry.getValue();
            final String gameval = entry.getKey();
            
            // Check if this object is favorited
            boolean isFavorited = favoriteObjects.contains(gameval);
            
            ObjectListSlot slot = new ObjectListSlot(
                    gameval,
                    objectType != null ? objectType.getId() : 0,
                    g -> {
                        // onSelect handler - deselect previous and select this one
                        if (selectedSlot != null)
                        {
                            selectedSlot.setSelected(false);
                        }
                        ObjectListSlot clickedSlot = slotsByGameval.get(g);
                        if (clickedSlot != null)
                        {
                            selectedSlot = clickedSlot;
                            clickedSlot.setSelected(true);
                            // Update ObjectAction with selected gameval
                            plugin.getObjectAction().setSelectedGameval(g);
                            
                            // Add to recent objects (maintains order, removes duplicates)
                            recentObjects.remove(g); // Remove if already exists
                            recentObjects.add(g); // Add to end (most recent)
                            
                            // Limit to MAX_RECENT_OBJECTS, remove oldest if needed
                            while (recentObjects.size() > MAX_RECENT_OBJECTS) {
                                String oldest = recentObjects.iterator().next();
                                recentObjects.remove(oldest);
                            }
                            
                            // Save to disk
                            saveRecentObjects();
                        }
                    },
                    g -> {
                        // onEdit handler
                    },
                    g -> {
                        // onToggleFavorite handler
                        boolean wasFavorited = favoriteObjects.contains(g);
                        if (wasFavorited) {
                            favoriteObjects.remove(g);
                        } else {
                            favoriteObjects.add(g);
                        }
                        saveFavorites();
                        
                        // Update the specific slot's favorite state immediately
                        ObjectListSlot slotToUpdate = slotsByGameval.get(g);
                        if (slotToUpdate != null) {
                            slotToUpdate.setFavorite(!wasFavorited, favoriteIcon);
                        }
                    },
                    isFavorited,
                    favoriteIcon,
                    "all".equals(currentTab) // Only show icon in "all" tab
            );
            
            // Store slot in map for efficient lookup
            slotsByGameval.put(gameval, slot);

            // Make slot fill available width (resize with splitter)
            slot.setMaximumSize(new Dimension(Integer.MAX_VALUE, slot.getMaximumSize().height));
            slot.setPreferredSize(new Dimension(0, slot.getPreferredSize().height));

            objectsContainer.add(slot);

            // Add 1 pixel gap between slots (except after the last one)
            if (i < pageObjects.size() - 1)
            {
                objectsContainer.add(Box.createVerticalStrut(1));
            }
        }

        // Add glue to push content to top
        objectsContainer.add(Box.createVerticalGlue());

        // Refresh the panel
        objectsContainer.revalidate();
        objectsContainer.repaint();
        scrollPane.getVerticalScrollBar().setValue(0); // Scroll to top
    }
    
    private File getRecentObjectsFile() {
        String userHome = System.getProperty("user.home");
        Path saveDir = Paths.get(userHome, ".runelite", SAVE_DIR);
        
        try {
            Files.createDirectories(saveDir);
        } catch (IOException e) {
            log.warn("Failed to create save directory", e);
        }
        
        return saveDir.resolve(RECENT_OBJECTS_FILE).toFile();
    }
    
    private void loadRecentObjects() {
        File recentFile = getRecentObjectsFile();
        
        if (!recentFile.exists()) {
            log.debug("No recent objects file found");
            return;
        }
        
        try (FileReader reader = new FileReader(recentFile)) {
            Type listType = new TypeToken<List<String>>(){}.getType();
            List<String> loaded = gson.fromJson(reader, listType);
            
            if (loaded != null) {
                recentObjects.clear();
                // Limit to MAX_RECENT_OBJECTS when loading
                int startIndex = Math.max(0, loaded.size() - MAX_RECENT_OBJECTS);
                for (int i = startIndex; i < loaded.size(); i++) {
                    recentObjects.add(loaded.get(i));
                }
                log.info("Loaded {} recent objects from disk", recentObjects.size());
            }
        } catch (IOException e) {
            log.warn("Failed to load recent objects from disk", e);
        }
    }
    
    private void saveRecentObjects() {
        File recentFile = getRecentObjectsFile();
        
        try (FileWriter writer = new FileWriter(recentFile)) {
            // Convert LinkedHashSet to List to preserve order
            List<String> recentList = new ArrayList<>(recentObjects);
            gson.toJson(recentList, writer);
            log.debug("Saved {} recent objects to disk", recentObjects.size());
        } catch (IOException e) {
            log.warn("Failed to save recent objects to disk", e);
        }
    }
    
    private File getFavoritesFile() {
        String userHome = System.getProperty("user.home");
        Path saveDir = Paths.get(userHome, ".runelite", SAVE_DIR);
        
        try {
            Files.createDirectories(saveDir);
        } catch (IOException e) {
            log.warn("Failed to create save directory", e);
        }
        
        return saveDir.resolve(FAVORITES_OBJECTS_FILE).toFile();
    }
    
    private void loadFavorites() {
        File favoritesFile = getFavoritesFile();
        
        if (!favoritesFile.exists()) {
            log.debug("No favorites file found");
            return;
        }
        
        try (FileReader reader = new FileReader(favoritesFile)) {
            Type listType = new TypeToken<List<String>>(){}.getType();
            List<String> loaded = gson.fromJson(reader, listType);
            
            if (loaded != null) {
                favoriteObjects.clear();
                favoriteObjects.addAll(loaded);
                log.info("Loaded {} favorite objects from disk", favoriteObjects.size());
            }
        } catch (IOException e) {
            log.warn("Failed to load favorites from disk", e);
        }
    }
    
    private void saveFavorites() {
        File favoritesFile = getFavoritesFile();
        
        try (FileWriter writer = new FileWriter(favoritesFile)) {
            // Convert LinkedHashSet to List to preserve order
            List<String> favoritesList = new ArrayList<>(favoriteObjects);
            gson.toJson(favoritesList, writer);
            log.debug("Saved {} favorite objects to disk", favoriteObjects.size());
        } catch (IOException e) {
            log.warn("Failed to save favorites to disk", e);
        }
    }
}

