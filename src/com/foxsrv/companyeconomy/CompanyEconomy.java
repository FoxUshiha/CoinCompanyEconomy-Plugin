package com.foxsrv.companyeconomy;

import com.foxsrv.coincard.CoinCardPlugin.CoinCardAPI;
import com.foxsrv.coincard.CoinCardPlugin.TransferCallback;
import com.foxsrv.coincard.CoinCardPlugin.BalanceCallback;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class CompanyEconomy extends JavaPlugin {

    private static final DecimalFormat COIN_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setDecimalSeparator('.');
        COIN_FORMAT = new DecimalFormat("0.########", symbols);
        COIN_FORMAT.setRoundingMode(RoundingMode.DOWN);
        COIN_FORMAT.setMinimumFractionDigits(0);
        COIN_FORMAT.setMaximumFractionDigits(8);
    }

    private CompanyManager companyManager;
    private CoinCardAPI coinCardAPI;
    private TransactionQueue transactionQueue;
    private SalaryTask salaryTask;
    private BukkitTask salaryBukkitTask;

    @Override
    public void onEnable() {
        getLogger().info("Starting CompanyEconomy v" + getDescription().getVersion() + "...");
        
        // Check if CoinCard is installed
        getLogger().info("Looking for CoinCard plugin...");
        
        Plugin coinCardPlugin = getServer().getPluginManager().getPlugin("CoinCard");
        if (coinCardPlugin == null) {
            getLogger().severe("CoinCard plugin not found! Is it installed?");
            getLogger().severe("Disabling CompanyEconomy...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        getLogger().info("CoinCard plugin found: v" + coinCardPlugin.getDescription().getVersion());
        
        // Try to get the API
        if (!setupCoinCardAPI()) {
            getLogger().severe("Could not get CoinCard API! Make sure CoinCard is properly loaded.");
            getLogger().severe("Disabling CompanyEconomy...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        getLogger().info("CoinCard API successfully obtained!");
        
        // Initialize components
        getLogger().info("Initializing components...");
        this.transactionQueue = new TransactionQueue(this, 1100); // 1100ms cooldown
        this.companyManager = new CompanyManager(this);
        this.companyManager.loadCompanies();
        
        // Start salary task (30 minutes = 36000 ticks)
        this.salaryTask = new SalaryTask(this);
        this.salaryBukkitTask = salaryTask.runTaskTimer(this, 6000L, 36000L);
        getLogger().info("Salary task scheduled (30 minutes interval)");

        // Register command
        PluginCommand companyCommand = getCommand("company");
        if (companyCommand != null) {
            CompanyCommand executor = new CompanyCommand();
            companyCommand.setExecutor(executor);
            companyCommand.setTabCompleter(executor);
            getLogger().info("Command /company registered");
        } else {
            getLogger().severe("Could not find command 'company' in plugin.yml!");
        }

        getLogger().info("CompanyEconomy v" + getDescription().getVersion() + " enabled successfully!");
        getLogger().info("Data folder: " + getDataFolder().getAbsolutePath());
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling CompanyEconomy...");
        
        if (salaryBukkitTask != null) {
            salaryBukkitTask.cancel();
            getLogger().info("Salary task cancelled");
        }
        if (transactionQueue != null) {
            transactionQueue.shutdown();
            getLogger().info("Transaction queue shut down");
        }
        if (companyManager != null) {
            companyManager.saveAll();
            getLogger().info("Company data saved");
        }
        
        getLogger().info("CompanyEconomy disabled.");
    }

    private boolean setupCoinCardAPI() {
        try {
            getLogger().info("Attempting to get CoinCardAPI via ServicesManager...");
            
            RegisteredServiceProvider<CoinCardAPI> provider = 
                getServer().getServicesManager().getRegistration(CoinCardAPI.class);

            if (provider != null) {
                coinCardAPI = provider.getProvider();
                getLogger().info("CoinCardAPI obtained via ServicesManager");
                return coinCardAPI != null;
            }
            
            getLogger().warning("Could not get CoinCardAPI via ServicesManager, trying alternative method...");
            
            // Try to get the plugin instance and then the API
            Plugin coinCard = getServer().getPluginManager().getPlugin("CoinCard");
            if (coinCard != null) {
                getLogger().info("CoinCard plugin instance found, trying to get API via reflection...");
                
                // Try to call getAPI() method
                try {
                    Method getAPIMethod = coinCard.getClass().getMethod("getAPI");
                    Object api = getAPIMethod.invoke(coinCard);
                    if (api instanceof CoinCardAPI) {
                        coinCardAPI = (CoinCardAPI) api;
                        getLogger().info("CoinCardAPI obtained via reflection");
                        return true;
                    }
                } catch (Exception e) {
                    getLogger().warning("Failed to get API via reflection: " + e.getMessage());
                }
                
                // Try to access static API field
                try {
                    java.lang.reflect.Field apiField = coinCard.getClass().getDeclaredField("api");
                    apiField.setAccessible(true);
                    Object api = apiField.get(coinCard);
                    if (api instanceof CoinCardAPI) {
                        coinCardAPI = (CoinCardAPI) api;
                        getLogger().info("CoinCardAPI obtained via field reflection");
                        return true;
                    }
                } catch (Exception e) {
                    getLogger().warning("Failed to get API via field reflection: " + e.getMessage());
                }
                
                // Try to access static getInstance method
                try {
                    Method getInstanceMethod = coinCard.getClass().getMethod("getInstance");
                    Object instance = getInstanceMethod.invoke(null);
                    if (instance != null) {
                        Method getAPIMethod2 = instance.getClass().getMethod("getAPI");
                        Object api = getAPIMethod2.invoke(instance);
                        if (api instanceof CoinCardAPI) {
                            coinCardAPI = (CoinCardAPI) api;
                            getLogger().info("CoinCardAPI obtained via getInstance()");
                            return true;
                        }
                    }
                } catch (Exception e) {
                    getLogger().warning("Failed to get API via getInstance: " + e.getMessage());
                }
            }
            
            getLogger().severe("All attempts to get CoinCardAPI failed!");
            return false;
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Unexpected error while setting up CoinCard API", e);
            return false;
        }
    }

    public CoinCardAPI getCoinCardAPI() {
        return coinCardAPI;
    }

    public CompanyManager getCompanyManager() {
        return companyManager;
    }

    public TransactionQueue getTransactionQueue() {
        return transactionQueue;
    }

    public static String formatCoin(double amount) {
        return formatCoin(BigDecimal.valueOf(amount));
    }

    public static String formatCoin(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        String formatted = COIN_FORMAT.format(amount);
        if (!formatted.contains(".")) {
            formatted += ".0";
        }
        return formatted;
    }

    public static BigDecimal truncate(double amount) {
        return BigDecimal.valueOf(amount).setScale(8, RoundingMode.DOWN);
    }

    // ====================================================
    // TRANSACTION QUEUE
    // ====================================================
    public static class TransactionQueue {
        private final CompanyEconomy plugin;
        private final Queue<QueuedTransfer> queue = new ConcurrentLinkedQueue<>();
        private final Map<String, TransferCallback> callbacks = new ConcurrentHashMap<>();
        private BukkitTask processorTask;
        private final AtomicLong lastProcessTime = new AtomicLong(0);
        private final long cooldownMs;

        public TransactionQueue(CompanyEconomy plugin, long cooldownMs) {
            this.plugin = plugin;
            this.cooldownMs = cooldownMs;
            startProcessor();
        }

        private void startProcessor() {
            processorTask = new BukkitRunnable() {
                @Override
                public void run() {
                    processQueue();
                }
            }.runTaskTimer(plugin, 20L, Math.max(1, cooldownMs / 50));
        }

        private void processQueue() {
            long now = System.currentTimeMillis();
            if (now - lastProcessTime.get() < cooldownMs) {
                return;
            }

            QueuedTransfer transfer = queue.poll();
            if (transfer == null) {
                return;
            }

            lastProcessTime.set(now);

            plugin.getCoinCardAPI().transfer(
                transfer.fromCard, 
                transfer.toCard, 
                transfer.amount, 
                new TransferCallback() {
                    @Override
                    public void onSuccess(String txId, double amount) {
                        TransferCallback callback = callbacks.remove(transfer.id);
                        if (callback != null) {
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    callback.onSuccess(txId, amount);
                                }
                            }.runTask(plugin);
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        TransferCallback callback = callbacks.remove(transfer.id);
                        if (callback != null) {
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    callback.onFailure(error);
                                }
                            }.runTask(plugin);
                        }
                    }
                }
            );
        }

        public void enqueue(String fromCard, String toCard, double amount, TransferCallback callback) {
            String id = UUID.randomUUID().toString();
            queue.add(new QueuedTransfer(id, fromCard, toCard, amount));
            if (callback != null) {
                callbacks.put(id, callback);
            }
        }

        public void shutdown() {
            if (processorTask != null) {
                processorTask.cancel();
            }
            queue.clear();
            callbacks.clear();
        }

        private static class QueuedTransfer {
            String id;
            String fromCard;
            String toCard;
            double amount;

            QueuedTransfer(String id, String fromCard, String toCard, double amount) {
                this.id = id;
                this.fromCard = fromCard;
                this.toCard = toCard;
                this.amount = amount;
            }
        }
    }

    // ====================================================
    // COMPANY MANAGER
    // ====================================================
    public static class CompanyManager {

        private final CompanyEconomy plugin;
        private final Map<String, Company> companies = new ConcurrentHashMap<>();
        private File companiesFolder;

        public CompanyManager(CompanyEconomy plugin) {
            this.plugin = plugin;
            setupFolder();
        }

        private void setupFolder() {
            companiesFolder = new File(plugin.getDataFolder(), "companies");

            if (!companiesFolder.exists()) {
                if (companiesFolder.mkdirs()) {
                    plugin.getLogger().info("Created companies folder.");
                }
            }
        }

        private void createDefaultCompanyIfMissing() {
            File defaultFile = new File(companiesFolder, "defaultCompany.yml");

            if (defaultFile.exists()) {
                return;
            }

            try {
                defaultFile.createNewFile();

                YamlConfiguration config = new YamlConfiguration();

                config.set("displayName", "Default Company");
                config.set("card", "e1301fadfc35");

                // ===== GLOBAL COMMANDS =====
                config.set("commands.on-fire", Arrays.asList(
                    "say %player% has been fired!"
                ));

                // ===== GROUP 1 (OWNER) =====
                config.set("groups.1.tag", "Owner");
                config.set("groups.1.salary", 0.00000001);
                config.set("groups.1.permissions.can-hire", true);
                config.set("groups.1.permissions.can-fire", true);
                config.set("groups.1.permissions.can-deposit", true);
                config.set("groups.1.permissions.can-withdraw", true);
                config.set("groups.1.commands.on-hire", Arrays.asList(
                    "say %player% is now the owner!"
                ));

                // ===== CONTRACT =====
                config.set("contract.enabled", true);
                config.set("contract.auto-send-on-hire", true);
                config.set("contract.lines", Arrays.asList(
                    "&6Employment Contract - Default Company",
                    "&7--------------------------------------",
                    "&7You agree to follow company rules.",
                    "&7Breaking rules may result in termination.",
                    "&aSalary will be paid every 30 minutes.",
                    "&7--------------------------------------"
                ));

                // ===== DEFAULT EMPLOYEE =====
                config.set("data.Steve.group", 1);

                config.save(defaultFile);

                plugin.getLogger().info("defaultCompany.yml created with Steve as default owner.");

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to create defaultCompany.yml");
                e.printStackTrace();
            }
        }

        public void loadCompanies() {
            companies.clear();
            createDefaultCompanyIfMissing();

            File[] files = companiesFolder.listFiles(
                (dir, name) -> name.toLowerCase().endsWith(".yml")
            );

            if (files == null || files.length == 0) {
                plugin.getLogger().warning("No company files found.");
                return;
            }

            for (File file : files) {
                try {
                    Company company = new Company(plugin, file);
                    companies.put(company.getName().toLowerCase(), company);
                    plugin.getLogger().info("Loaded company: " + company.getName());
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to load company file: " + file.getName());
                    e.printStackTrace();
                }
            }

            plugin.getLogger().info("Total companies loaded: " + companies.size());
        }

        public void saveAll() {
            for (Company company : companies.values()) {
                company.save();
            }
        }

        public void reload() {
            // Salva os dados atuais em cache (opcional, para preservar alterações não salvas)
            saveAll();
            
            // Limpa completamente o cache
            companies.clear();
            
            // Carrega tudo novamente do disco
            loadCompanies();
            
            plugin.getLogger().info("Companies reloaded from disk - cache cleared");
        }

        public Company getCompany(String name) {
            if (name == null) return null;
            return companies.get(name.toLowerCase());
        }

        public List<Company> getCompanies() {
            return new ArrayList<>(companies.values());
        }

        public List<String> getCompanyNames() {
            return companies.values().stream()
                    .map(Company::getName)
                    .sorted(String::compareToIgnoreCase)
                    .collect(Collectors.toList());
        }

        public Company resolveCompanyForExecutor(String executorName, String companyArg, String permission) {
            if (companyArg != null) {
                Company company = getCompany(companyArg);
                if (company != null && company.hasPermission(executorName, permission)) {
                    return company;
                }
                return null;
            }

            return companies.values().stream()
                    .sorted(Comparator.comparing(Company::getName))
                    .filter(c -> c.hasPermission(executorName, permission))
                    .findFirst()
                    .orElse(null);
        }

        public Company createCompany(String name, String card) {
            String fileName = name + ".yml";
            File file = new File(companiesFolder, fileName);

            if (file.exists()) {
                return null;
            }

            try {
                if (file.createNewFile()) {
                    YamlConfiguration config = new YamlConfiguration();
                    config.set("displayName", name);
                    config.set("card", card);
                    config.save(file);

                    Company company = new Company(plugin, file);
                    companies.put(name.toLowerCase(), company);
                    return company;
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Could not create company: " + name);
                e.printStackTrace();
            }

            return null;
        }

        public boolean deleteCompany(String name) {
            Company company = getCompany(name);
            if (company == null) return false;

            File file = new File(companiesFolder, company.getName() + ".yml");
            companies.remove(name.toLowerCase());
            return file.delete();
        }
    }

    // ====================================================
    // COMPANY
    // ====================================================
    public static class Company {

        private final CompanyEconomy plugin;
        private final File file;
        private final YamlConfiguration config;

        private final String name;
        private final String displayName;
        private final String cardId;

        // PlayerName (lowercase) -> groupId
        private final Map<String, Integer> employees = new ConcurrentHashMap<>();

        public Company(CompanyEconomy plugin, File file) {
            this.plugin = plugin;
            this.file = file;
            this.config = YamlConfiguration.loadConfiguration(file);

            this.name = file.getName().replace(".yml", "");
            this.displayName = config.getString("displayName", name);
            this.cardId = config.getString("card", "");

            loadEmployees();
        }

        private void loadEmployees() {
            ConfigurationSection dataSection = config.getConfigurationSection("data");
            if (dataSection == null) return;

            for (String playerName : dataSection.getKeys(false)) {
                int group = dataSection.getInt(playerName + ".group");
                employees.put(playerName.toLowerCase(), group);
            }
        }

        public String getName() {
            return name;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getCardId() {
            return cardId;
        }

        public void getBalance(BalanceCallback callback) {
            if (cardId == null || cardId.isEmpty()) {
                callback.onResult(0, "Company has no card set");
                return;
            }
            plugin.getCoinCardAPI().getBalance(cardId, callback);
        }

        public Map<String, Integer> getEmployees() {
            return employees;
        }

        public boolean isEmployee(String playerName) {
            if (playerName == null) return false;
            return employees.containsKey(playerName.toLowerCase());
        }

        public int getEmployeeGroup(String playerName) {
            if (playerName == null) return -1;
            return employees.getOrDefault(playerName.toLowerCase(), -1);
        }

        public int getGroupIdByName(String roleName) {
            if (roleName == null) return -1;

            ConfigurationSection groups = config.getConfigurationSection("groups");
            if (groups == null) return -1;

            for (String id : groups.getKeys(false)) {
                ConfigurationSection section = groups.getConfigurationSection(id);
                if (section == null) continue;

                String tag = section.getString("tag");
                if (tag != null && tag.equalsIgnoreCase(roleName)) {
                    return Integer.parseInt(id);
                }
            }

            return -1;
        }

        public List<String> getGroupTags() {
            List<String> tags = new ArrayList<>();

            ConfigurationSection groups = config.getConfigurationSection("groups");
            if (groups == null) return tags;

            for (String id : groups.getKeys(false)) {
                ConfigurationSection section = groups.getConfigurationSection(id);
                if (section == null) continue;

                String tag = section.getString("tag");
                if (tag != null) {
                    tags.add(tag);
                }
            }

            return tags;
        }

        public double getSalary(int groupId) {
            return config.getDouble("groups." + groupId + ".salary", 0.0);
        }

        public boolean hasPermission(String playerName, String permission) {
            int group = getEmployeeGroup(playerName);
            if (group == -1) return false;

            return config.getBoolean(
                "groups." + group + ".permissions." + permission,
                false
            );
        }

        public void addEmployee(String playerName, int groupId) {
            if (playerName == null) return;

            String key = playerName.toLowerCase();
            employees.put(key, groupId);

            config.set("data." + playerName + ".group", groupId);
            save();
        }

        public void removeEmployee(String playerName) {
            if (playerName == null) return;

            String key = playerName.toLowerCase();
            employees.remove(key);

            config.set("data." + playerName, null);
            save();
        }

        public void deposit(double amount, TransferCallback callback) {
            if (cardId == null || cardId.isEmpty()) {
                if (callback != null) {
                    callback.onFailure("Company has no card set");
                }
                return;
            }
        }

        public void withdraw(double amount, TransferCallback callback) {
            if (cardId == null || cardId.isEmpty()) {
                if (callback != null) {
                    callback.onFailure("Company has no card set");
                }
                return;
            }
        }

        public void executeGroupCommands(String type, String playerName, int groupId) {
            List<String> commands = config.getStringList(
                "groups." + groupId + ".commands." + type
            );

            if (commands == null || commands.isEmpty()) return;

            for (String cmd : commands) {
                if (cmd == null || cmd.isEmpty()) continue;

                String parsed = cmd.replace("%player%", playerName);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        }

        public void executeGlobalCommands(String type, String playerName) {
            List<String> commands = config.getStringList("commands." + type);

            if (commands == null || commands.isEmpty()) return;

            for (String cmd : commands) {
                if (cmd == null || cmd.isEmpty()) continue;

                String parsed = cmd.replace("%player%", playerName);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        }

        public void save() {
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save company file: " + name);
                e.printStackTrace();
            }
        }
    }

    // ====================================================
    // SALARY TASK
    // ====================================================
    public static class SalaryTask extends BukkitRunnable {

        private final CompanyEconomy plugin;

        public SalaryTask(CompanyEconomy plugin) {
            this.plugin = plugin;
        }

        @Override
        public void run() {
            plugin.getCompanyManager().getCompanies().forEach(company -> {

                company.getEmployees().forEach((playerName, groupId) -> {

                    Player player = Bukkit.getPlayerExact(playerName);

                    // Se offline, não paga
                    if (player == null) return;

                    double salary = company.getSalary(groupId);
                    if (salary <= 0) return;

                    // Check company balance first
                    plugin.getCoinCardAPI().getBalance(company.getCardId(), new BalanceCallback() {
                        @Override
                        public void onResult(double balance, String error) {
                            if (error != null && !error.isEmpty()) {
                                player.sendMessage(ChatColor.RED + 
                                    "Salary error: " + error);
                                return;
                            }

                            if (balance >= salary) {
                                // Get player's card
                                String playerCard = plugin.getCoinCardAPI().getPlayerCard(player.getUniqueId());
                                if (playerCard == null || playerCard.isEmpty()) {
                                    player.sendMessage(ChatColor.RED + 
                                        "You don't have a card set! Cannot receive salary.");
                                    return;
                                }
                                
                                // Queue the salary payment
                                plugin.getTransactionQueue().enqueue(
                                    company.getCardId(),
                                    playerCard,
                                    salary,
                                    new TransferCallback() {
                                        @Override
                                        public void onSuccess(String txId, double amount) {
                                            player.sendMessage(ChatColor.GREEN + 
                                                "You received your salary: " + 
                                                formatCoin(amount) + " coins (TX: " + txId + ")");
                                        }

                                        @Override
                                        public void onFailure(String error) {
                                            player.sendMessage(ChatColor.RED + 
                                                "Salary payment failed: " + error);
                                        }
                                    }
                                );
                            } else {
                                player.sendMessage(ChatColor.RED + 
                                    "You did not receive salary because the company doesn't have enough funds.");
                            }
                        }
                    });
                });
            });
        }
    }

    // ====================================================
    // COMPANY COMMAND
    // ====================================================
    public class CompanyCommand implements CommandExecutor, TabCompleter {

        @Override
        public boolean onCommand(CommandSender sender, Command command,
                                 String label, String[] args) {

            if (args.length == 0) {
                return handleInfo(sender, null);
            }

            String sub = args[0].toLowerCase();

            switch (sub) {
                case "hire":
                    return handleHire(sender, args);
                case "fire":
                    return handleFire(sender, args);
                case "leave":
                    return handleLeave(sender, args);
                case "deposit":
                    return handleDeposit(sender, args);
                case "withdraw":
                    return handleWithdraw(sender, args);
                case "reload":
                    return handleReload(sender);
                case "info":
                    return handleInfo(sender, args.length >= 2 ? args[1] : null);
                default:
                    Company company = companyManager.getCompany(args[0]);
                    if (company != null) return handleInfo(sender, args[0]);
                    sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
                    return true;
            }
        }

        private boolean handleInfo(CommandSender sender, String companyName) {
            Company company = (companyName != null)
                    ? companyManager.getCompany(companyName)
                    : companyManager.getCompanies().stream()
                    .sorted(Comparator.comparing(Company::getName))
                    .findFirst().orElse(null);

            if (company == null) {
                sender.sendMessage(ChatColor.RED + "Company not found.");
                return true;
            }

            sender.sendMessage(ChatColor.GOLD + "=== " + company.getDisplayName() + " ===");

            // Get balance asynchronously
            company.getBalance(new BalanceCallback() {
                @Override
                public void onResult(double balance, String error) {
                    if (error != null && !error.isEmpty()) {
                        sender.sendMessage(ChatColor.RED + "Error fetching balance: " + error);
                    } else {
                        sender.sendMessage(ChatColor.YELLOW + "Balance: " + 
                            ChatColor.GREEN + formatCoin(balance) + " coins");
                    }

                    sender.sendMessage(ChatColor.YELLOW + "Members:");

                    for (Map.Entry<String, Integer> entry : company.getEmployees().entrySet()) {
                        String playerName = entry.getKey();
                        int group = entry.getValue();

                        String role = company.getGroupTags().stream()
                                .filter(tag -> company.getGroupIdByName(tag) == group)
                                .findFirst().orElse("Unknown");

                        sender.sendMessage(ChatColor.GRAY + "- "
                                + playerName + ChatColor.DARK_GRAY + " (" + role + ")");
                    }
                }
            });

            return true;
        }

        private boolean handleHire(CommandSender sender, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player executor = (Player) sender;

            if (args.length < 4) {
                executor.sendMessage(ChatColor.RED +
                        "Usage: /company hire <player> <company> <role>");
                return true;
            }

            String targetName = args[1];
            String companyName = args[2];
            String roleName = args[3];

            Company company = companyManager.getCompany(companyName);

            if (company == null ||
                    !company.hasPermission(executor.getName(), "can-hire") ||
                    !company.isEmployee(executor.getName())) {
                executor.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }

            if (company.isEmployee(targetName)) {
                executor.sendMessage(ChatColor.RED + "Already employed.");
                return true;
            }

            int executorGroup = company.getEmployeeGroup(executor.getName());
            int targetGroup = company.getGroupIdByName(roleName);

            if (targetGroup == -1 || executorGroup >= targetGroup) {
                executor.sendMessage(ChatColor.RED + "Invalid role.");
                return true;
            }

            company.addEmployee(targetName, targetGroup);
            company.executeGroupCommands("on-hire", targetName, targetGroup);

            executor.sendMessage(ChatColor.GREEN + "Player hired.");
            return true;
        }

        private boolean handleFire(CommandSender sender, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player executor = (Player) sender;

            if (args.length < 3) {
                executor.sendMessage(ChatColor.RED +
                        "Usage: /company fire <player> <company>");
                return true;
            }

            String targetName = args[1];
            String companyName = args[2];

            Company company = companyManager.getCompany(companyName);

            if (company == null ||
                    !company.hasPermission(executor.getName(), "can-fire") ||
                    !company.isEmployee(executor.getName())) {
                executor.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }

            if (!company.isEmployee(targetName)) {
                executor.sendMessage(ChatColor.RED + "Not in company.");
                return true;
            }

            company.removeEmployee(targetName);
            company.executeGlobalCommands("on-fire", targetName);

            executor.sendMessage(ChatColor.GREEN + "Player fired.");
            return true;
        }

        private boolean handleLeave(CommandSender sender, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;

            if (args.length < 2) {
                player.sendMessage(ChatColor.RED +
                        "Usage: /company leave <company>");
                return true;
            }

            String companyName = args[1];
            Company company = companyManager.getCompany(companyName);

            if (company == null || !company.isEmployee(player.getName())) {
                player.sendMessage(ChatColor.RED + "You are not in this company.");
                return true;
            }

            company.removeEmployee(player.getName());
            company.executeGlobalCommands("on-fire", player.getName());

            player.sendMessage(ChatColor.RED + "You left the company.");
            return true;
        }

        private boolean handleDeposit(CommandSender sender, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;

            if (args.length < 3) {
                player.sendMessage(ChatColor.RED +
                        "Usage: /company deposit <company> <amount>");
                return true;
            }

            String companyName = args[1];
            Company company = companyManager.getCompany(companyName);

            if (company == null ||
                    !company.hasPermission(player.getName(), "can-deposit")) {
                player.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }

            double amount;
            try {
                amount = Double.parseDouble(args[2]);
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "Invalid amount.");
                return true;
            }

            if (amount <= 0) {
                player.sendMessage(ChatColor.RED + "Amount must be greater than 0.");
                return true;
            }

            // Get player's card
            String playerCard = coinCardAPI.getPlayerCard(player.getUniqueId());
            if (playerCard == null || playerCard.isEmpty()) {
                player.sendMessage(ChatColor.RED + "You don't have a card set! Use /coin card <card>");
                return true;
            }

            // Check player balance
            coinCardAPI.getBalance(playerCard, new BalanceCallback() {
                @Override
                public void onResult(double balance, String error) {
                    if (error != null && !error.isEmpty()) {
                        player.sendMessage(ChatColor.RED + "Error checking balance: " + error);
                        return;
                    }

                    if (balance < amount) {
                        player.sendMessage(ChatColor.RED + "Not enough coins. You have " + 
                            formatCoin(balance) + " coins.");
                        return;
                    }

                    // Queue the transfer
                    transactionQueue.enqueue(
                        playerCard,
                        company.getCardId(),
                        amount,
                        new TransferCallback() {
                            @Override
                            public void onSuccess(String txId, double amount) {
                                player.sendMessage(ChatColor.GREEN +
                                    "Deposited " + formatCoin(amount) + 
                                    " coins to " + company.getDisplayName() + 
                                    " (TX: " + txId + ")");
                            }

                            @Override
                            public void onFailure(String error) {
                                player.sendMessage(ChatColor.RED +
                                    "Deposit failed: " + error);
                            }
                        }
                    );
                }
            });

            return true;
        }

        private boolean handleWithdraw(CommandSender sender, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;

            if (args.length < 3) {
                player.sendMessage(ChatColor.RED +
                        "Usage: /company withdraw <company> <amount>");
                return true;
            }

            String companyName = args[1];
            Company company = companyManager.getCompany(companyName);

            if (company == null ||
                    !company.hasPermission(player.getName(), "can-withdraw")) {
                player.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }

            double amount;
            try {
                amount = Double.parseDouble(args[2]);
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "Invalid amount.");
                return true;
            }

            if (amount <= 0) {
                player.sendMessage(ChatColor.RED + "Amount must be greater than 0.");
                return true;
            }

            // Get player's card
            String playerCard = coinCardAPI.getPlayerCard(player.getUniqueId());
            if (playerCard == null || playerCard.isEmpty()) {
                player.sendMessage(ChatColor.RED + "You don't have a card set! Use /coin card <card>");
                return true;
            }

            // Check company balance
            coinCardAPI.getBalance(company.getCardId(), new BalanceCallback() {
                @Override
                public void onResult(double balance, String error) {
                    if (error != null && !error.isEmpty()) {
                        player.sendMessage(ChatColor.RED + "Error checking company balance: " + error);
                        return;
                    }

                    if (balance < amount) {
                        player.sendMessage(ChatColor.RED + "Company doesn't have enough coins. Balance: " + 
                            formatCoin(balance) + " coins.");
                        return;
                    }

                    // Queue the transfer (company pays to player)
                    transactionQueue.enqueue(
                        company.getCardId(),
                        playerCard,
                        amount,
                        new TransferCallback() {
                            @Override
                            public void onSuccess(String txId, double amount) {
                                player.sendMessage(ChatColor.GREEN +
                                    "Withdrew " + formatCoin(amount) + 
                                    " coins from " + company.getDisplayName() + 
                                    " (TX: " + txId + ")");
                            }

                            @Override
                            public void onFailure(String error) {
                                player.sendMessage(ChatColor.RED +
                                    "Withdrawal failed: " + error);
                            }
                        }
                    );
                }
            });

            return true;
        }

        private boolean handleReload(CommandSender sender) {
            if (!sender.hasPermission("company.reload")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }

            companyManager.reload();
            sender.sendMessage(ChatColor.GREEN + "CompanyEconomy reloaded.");
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender,
                                          Command command,
                                          String alias,
                                          String[] args) {

            try {
                if (args.length == 1) {
                    List<String> subs = new ArrayList<>(Arrays.asList(
                        "hire", "fire", "leave", "deposit", "withdraw", "reload", "info"
                    ));
                    return filter(subs, args[0]);
                }

                if (!(sender instanceof Player)) {
                    return Collections.emptyList();
                }

                Player player = (Player) sender;
                String sub = args[0].toLowerCase();

                if (sub.equals("hire")) {
                    if (args.length == 2) {
                        return filter(getAllPlayerNames(), args[1]);
                    }
                    if (args.length == 3) {
                        return filter(getExecutorCompanies(player), args[2]);
                    }
                    if (args.length == 4) {
                        Company company = companyManager.getCompany(args[2]);
                        if (company != null) {
                            return filter(company.getGroupTags(), args[3]);
                        }
                    }
                }

                if (sub.equals("fire")) {
                    if (args.length == 2) {
                        return filter(getAllPlayerNames(), args[1]);
                    }
                    if (args.length == 3) {
                        return filter(getExecutorCompanies(player), args[2]);
                    }
                }

                if (sub.equals("deposit") || sub.equals("withdraw")) {
                    if (args.length == 2) {
                        return filter(getExecutorCompanies(player), args[1]);
                    }
                }

                if (sub.equals("leave") && args.length == 2) {
                    return filter(getExecutorCompanies(player), args[1]);
                }

                if (sub.equals("info") && args.length == 2) {
                    return filter(companyManager.getCompanyNames(), args[1]);
                }

            } catch (Exception ignored) {}

            return Collections.emptyList();
        }

        private List<String> getExecutorCompanies(Player player) {
            return companyManager.getCompanies().stream()
                    .filter(c -> c.isEmployee(player.getName()))
                    .map(Company::getName)
                    .sorted()
                    .collect(Collectors.toList());
        }

        private List<String> getAllPlayerNames() {
            return Arrays.stream(Bukkit.getOfflinePlayers())
                    .map(OfflinePlayer::getName)
                    .filter(Objects::nonNull)
                    .sorted()
                    .collect(Collectors.toList());
        }

        private List<String> filter(List<String> list, String current) {
            return list.stream()
                    .filter(s -> s.toLowerCase().startsWith(current.toLowerCase()))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }
}
