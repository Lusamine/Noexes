package me.mdbell.noexs.ui.controllers;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.beans.property.DoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.AnchorPane;
import me.mdbell.javafx.control.AddressSpinner;
import me.mdbell.javafx.control.FormattedTableCell;
import me.mdbell.javafx.control.HexSpinner;
import me.mdbell.noexs.core.Debugger;
import me.mdbell.noexs.core.EMemoryRegion;
import me.mdbell.noexs.core.MemoryType;
import me.mdbell.noexs.dump.DumpRegionSupplier;
import me.mdbell.noexs.ui.models.ConditionType;
import me.mdbell.noexs.ui.models.ConversionType;
import me.mdbell.noexs.ui.models.DataType;
import me.mdbell.noexs.ui.models.Range;
import me.mdbell.noexs.ui.models.RangeType;
import me.mdbell.noexs.ui.models.SearchType;
import me.mdbell.noexs.ui.models.SearchValueModel;
import me.mdbell.noexs.ui.services.MemorySearchService;
import me.mdbell.noexs.ui.services.SearchResult;
import me.mdbell.util.HexUtils;

public class SearchController implements IController {

    private static final Logger logger = LogManager.getLogger(SearchController.class);

    private MainController mc;

    @FXML
    ComboBox<RangeType> searchType;

    @FXML
    ComboBox<DataType> dataTypeDropdown;

    @FXML
    ComboBox<SearchType> searchConditionTypeDropdown;
    @FXML
    ComboBox<ConditionType> searchConditionDropdown;

    @FXML
    AddressSpinner searchStart;

    @FXML
    AddressSpinner searchEnd;

    @FXML
    AddressSpinner mainStart;

    @FXML
    AddressSpinner mainsearchEnd;

    @FXML
    TextField fileDumpSuffix;

    @FXML
    TextField floatValue;

    @FXML
    ChoiceBox<ConversionType> conversionType;

    @FXML
    HexSpinner knownValue;

    @FXML
    AnchorPane searchTabPage;

    @FXML
    TableView<SearchValueModel> searchResults;

    @FXML
    TableColumn<SearchValueModel, Number> searchAddr;
    @FXML
    TableColumn<SearchValueModel, Number> oldValue;
    @FXML
    TableColumn<SearchValueModel, Number> newValue;
    @FXML
    TableColumn<SearchValueModel, Number> diff;

    @FXML
    HexSpinner pokeValue;

    @FXML
    TitledPane searchOptions;

    @FXML
    Label conditionLabel;

    @FXML
    Button pageLeft;

    @FXML
    Button pageRight;

    @FXML
    Label pageLabel;

    private SearchResult result;

    private Service<?> runningService = null;
    private final MemorySearchService searchService = new MemorySearchService();

    private ObservableList<SearchValueModel> resultList;

    private int currentPage = 0;

    @FXML
    public void initialize() {
        searchType.getItems().addAll(RangeType.values());

        searchType.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            boolean b = newValue != RangeType.RANGE;
            searchStart.setDisable(b);
            searchEnd.setDisable(b);
        });
        searchType.getSelectionModel().select(RangeType.ALL);

        dataTypeDropdown.getItems().addAll(DataType.values());
        dataTypeDropdown.getSelectionModel().select(DataType.INT); // Default is 32-bit

        dataTypeDropdown.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            int size = newValue.getSize() * 2;
            knownValue.setSize(size);
            pokeValue.setSize(size);
        });

        searchConditionTypeDropdown.getItems().addAll(SearchType.values());
        searchConditionTypeDropdown.getSelectionModel().select(SearchType.KNOWN); // Default is SPEC Value

        searchConditionTypeDropdown.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> {
                    boolean b = newValue == SearchType.UNKNOWN;
                    knownValue.setDisable(b);
                    searchConditionDropdown.setDisable(b);
                    if (b) {
                        searchConditionDropdown.setValue(ConditionType.EQUALS);
                    }
                    updateCondition();
                });

        searchConditionDropdown.getItems().addAll(ConditionType.values());
        searchConditionDropdown.getSelectionModel().select(ConditionType.EQUALS);

        searchConditionDropdown.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> updateCondition());

        knownValue.valueProperty().addListener((observable, oldValue, newValue) -> updateCondition());
        knownValue.getEditor().textProperty().addListener((observable, oldValue, newValue) -> updateCondition());

        conversionType.getItems().addAll(ConversionType.values());
        conversionType.getSelectionModel().select(ConversionType.U32); // TODO save/store this

        resultList = FXCollections.observableArrayList();

        searchResults.setItems(resultList);
        searchResults.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        searchResults.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                pokeValue.getValueFactory().setValue(newValue.getNewValue());
            }
        });

        Function<Number, String> valueFormatter = value -> HexUtils.pad('0', result.getDataType().getSize() * 2,
                Long.toUnsignedString(value.longValue(), 16));

        searchAddr
                .setCellFactory(param -> new FormattedTableCell<>(value -> HexUtils.formatAddress(value.longValue())));
        oldValue.setCellFactory(param -> new FormattedTableCell<>(valueFormatter));
        newValue.setCellFactory(param -> new FormattedTableCell<>(valueFormatter));
        diff.setCellFactory(param -> new FormattedTableCell<>(value -> {
            long l = value.longValue();
            return (l < 0 ? "-" : "") + valueFormatter.apply(Math.abs(l));
        }));

        searchAddr.setCellValueFactory(param -> param.getValue().addrProperty());
        oldValue.setCellValueFactory(param -> param.getValue().oldValueProperty());
        newValue.setCellValueFactory(param -> param.getValue().newValueProperty());
        diff.setCellValueFactory(param -> param.getValue().diffProperty());

        ContextMenu cm = new ContextMenu();
        MenuItem memoryView = new MenuItem("Memory Viewer");
        memoryView.setOnAction(event -> {
            SearchValueModel m = searchResults.getSelectionModel().getSelectedItem();
            if (m == null) {
                return;
            }
            mc.memory().setViewAddress(m.getAddr());
            mc.setTab(MainController.Tab.MEMORY_VIEWER);
        });
        MenuItem watchList = new MenuItem("Watch List");
        watchList.setOnAction(event -> {
            List<SearchValueModel> ms = searchResults.getSelectionModel().getSelectedItems();
            if (ms == null) {
                return;
            }
            for (SearchValueModel m : ms) {
                mc.watch().addAddr(m.getAddr());
            }
            mc.setTab(MainController.Tab.WATCH_LIST);
        });
        MenuItem pointerSearch = new MenuItem("Pointer Search");
        pointerSearch.setOnAction(event -> {
            SearchValueModel m = searchResults.getSelectionModel().getSelectedItem();
            if (m == null) {
                return;
            }
            mc.pointer().addressSpinner.getValueFactory().setValue(m.getAddr());
            mc.setTab(MainController.Tab.POINTER_SEARCH);
        });

        MenuItem cheatMaker = new MenuItem("Cheat Maker");
        cheatMaker.setOnAction(event -> {
            List<SearchValueModel> ms = searchResults.getSelectionModel().getSelectedItems();
            if (ms == null) {
                return;
            }

            ToolsController tc = mc.toolsTabPageController;
            int cheatIndex = 0;
            for (SearchValueModel m : ms) {
                long address = m.getAddr();
                EMemoryRegion region = tc.getAddressMemoryRegion(address);
                long offset = tc.getOffset(address, region);
                String pointerStr = "\"Set pointer " + ++cheatIndex + "\"\n";
                pointerStr += "[" + region + " + 0x" + HexUtils.formatAddress(offset)

                        + "] = (U32) 0x" + HexUtils.formatLong(m.getNewValue());

                mc.cheatMaker().cheatSource.appendText(pointerStr + "\n");
            }
            mc.setTab(MainController.Tab.CHEATS);
        });
        cm.getItems().addAll(memoryView, watchList, pointerSearch, cheatMaker);
        searchResults.contextMenuProperty().setValue(cm);
        updateCondition();
    }

    private void updateCondition() {
        String value = knownValue.getValueAsString();
        SearchType type = searchConditionTypeDropdown.getValue();
        ConditionType condition = searchConditionDropdown.getValue();

        String currentName = "CURRENT_VALUE";
        String prevName = "PREV_VALUE";
        String left = currentName;
        String cmp = condition.getOperator();
        String right;
        switch (type) {
            case UNKNOWN:
                right = "*";
                break;
            case PREVIOUS:
                right = prevName;
                break;
            case KNOWN:
                right = value;
                break;
            case DIFFERENT:
                left = "|" + currentName + " - " + prevName + "|";
                right = value;
                break;
            default:
                conditionLabel.setText("Invalid/Unknown condition!");
                return;
        }
        conditionLabel.setText(left + " " + cmp + " " + right);
    }

    public void setSearchRange(long start, long end) {
        setStart(start);
        setEnd(end);
    }

    public void mainsetSearchRange(long start, long end) {
        mainsetStart(start);
        mainsetEnd(end);
    }

    public void setStart(long start) {
        searchStart.getValueFactory().setValue(start);
        searchType.setValue(RangeType.RANGE);
    }

    public void setEnd(long end) {
        searchEnd.getValueFactory().setValue(end);
        searchType.setValue(RangeType.RANGE);
    }

    public void mainsetStart(long start) {
        mainStart.getValueFactory().setValue(start);
        searchType.setValue(RangeType.RANGE);
    }

    public void mainsetEnd(long end) {
        mainsearchEnd.getValueFactory().setValue(end);
        searchType.setValue(RangeType.RANGE);
    }

    public void convertToHex(ActionEvent event) {
        try {
            long l = Long.parseUnsignedLong(knownValue.getValueAsString());
            knownValue.getValueFactory().setValue(l);
        } catch (NumberFormatException ignored) {
        }
    }

    public void convertValueToHex(ActionEvent event) {
        try {
            switch (conversionType.getValue()) {
                case FLT:
                    Float f = Float.parseFloat(floatValue.getText());
                    knownValue.getValueFactory().setValue((long) Float.floatToIntBits(f));
                    break;
                case U32:
                    long l = Long.parseUnsignedLong(floatValue.getText());
                    knownValue.getValueFactory().setValue(l);
                    break;
                case S32:
                    long sl = Long.parseLong(floatValue.getText());
                    knownValue.getValueFactory().setValue(sl);
                    break;
            }
        } catch (NumberFormatException e) {
            // TODO ERROR CASE
        }

    }

    public void poke(ActionEvent event) {
        if (isServiceRunning()) {
            MainController.showMessage("Please wait for your previous task to finish!", Alert.AlertType.WARNING);
            return;
        }
        List<SearchValueModel> models = searchResults.getSelectionModel().getSelectedItems();
        long value = pokeValue.getValue();
        DataType t = dataTypeDropdown.getSelectionModel().getSelectedItem();
        Debugger debugger = mc.getDebugger();
        for (int i = 0; i < models.size(); i++) {
            SearchValueModel m = models.get(i);
            debugger.poke(t, m.getAddr(), value);
        }
    }

    public void onPageLeft(ActionEvent event) {
        currentPage--;
        updatePageInfo();
    }

    public void onPageRight(ActionEvent event) {
        currentPage++;
        updatePageInfo();
    }

    public void setMainController(MainController mc) {
        this.mc = mc;
    }

    @Override
    public void onConnect() {
        searchTabPage.setDisable(false);
    }

    @Override
    public void onDisconnect() {
        searchTabPage.setDisable(true);
    }

    public void onStartAction(ActionEvent event) {
        if (isServiceRunning()) {
            MainController.showMessage("Please wait for your current task to complete!", Alert.AlertType.WARNING);
            return;
        }
        DataType dataType = dataTypeDropdown.getValue();
        long known = knownValue.getValue();

        SearchType type = searchConditionTypeDropdown.getValue();
        ConditionType compareType = searchConditionDropdown.getValue();
        searchService.setConnection(mc.getDebugger());
        searchService.setSupplier(getDumpRegionSupplier(mc.getDebugger()));
        searchService.setFileDumpSuffix(fileDumpSuffix.getText());
        initSearch(type, compareType, dataType, known);
    }

    public void onMainStartAction(ActionEvent event) {
        if (isServiceRunning()) {
            MainController.showMessage("Please wait for your current task to complete!", Alert.AlertType.WARNING);
            return;
        }
        DataType dataType = dataTypeDropdown.getValue();
        long known = knownValue.getValue();

        SearchType type = searchConditionTypeDropdown.getValue();
        ConditionType compareType = searchConditionDropdown.getValue();
        searchService.setConnection(mc.getDebugger());
        searchService.setSupplier(getDumpRegionSupplier(mc.getDebugger()));
        searchService.setFileDumpSuffix(fileDumpSuffix.getText());
        initSearch(type, compareType, dataType, known);
    }

    public void onRestartAction(ActionEvent event) {
        if (isServiceRunning()) {
            MainController.showMessage("Please wait for your current search to complete (or cancel it)!",
                    Alert.AlertType.WARNING);
            return;
        }
        searchService.clear();
        DoubleProperty prog = mc.getProgressBar().progressProperty();
        prog.unbind();
        prog.setValue(0);
        if (result != null) {
            try {
                result.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        result = null;
        currentPage = 0;
        updatePageInfo();
        mc.setStatus("Search result cleared!");
    }

    public void onCancelAction(ActionEvent event) {
        if (runningService == null) {
            MainController.showMessage("No search has been run!", Alert.AlertType.INFORMATION);
            return;
        }
        if (!runningService.isRunning()) {
            MainController.showMessage("No search is running!", Alert.AlertType.WARNING);
        }
        if (runningService.cancel()) {
            mc.getProgressBar().progressProperty().unbind();
            mc.setStatus("Search Canceled");
            searchOptions.setDisable(false);
        }
    }

    public void onUndoAction(ActionEvent event) {
        if (isServiceRunning()) {
            MainController.showMessage("Please cancel/wait for the current task before undoing a search!",
                    Alert.AlertType.WARNING);
            return;
        }
        if (result == null) {
            MainController.showMessage("Please preform a search before trying to undo it!", Alert.AlertType.WARNING);
            return;
        }
        if (result.getPrev() == null) {
            MainController.showMessage("No previous search exists!", Alert.AlertType.WARNING);
            return;
        }
        SearchResult prev = result.getPrev();
        result.setPrev(null); // to prevent the result from closing the previous one
        try {
            result.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        result = prev;
        searchService.setPrevResult(result);
        updatePageInfo();
    }

    public void onLoadSearch(ActionEvent event) {
        File f = mc.browseFile(false, null, null, "Open...", "Search file", "*.srch");
        if (f == null) {
            return;
        }
        mc.setStatus("Loading search : " + f.getPath());

        logger.info("Loading search : {}", f);
        try {
            SearchResult sr = SearchResult.load(f);
            logger.info("SearchResult loaded : {}", sr);
            result = sr;
            searchService.setPrevResult(result);
            mc.setStatus("Done! Total Result:" + result.size());
            resultList.clear();
            currentPage = 0;
            searchOptions.setDisable(false);
            if (result.getType() == SearchType.UNKNOWN) {
                searchConditionTypeDropdown.setValue(SearchType.PREVIOUS);
                searchConditionDropdown.setValue(ConditionType.NOT_EQUAL);
            }
            updatePageInfo();
        } catch (IOException e) {
            mc.setStatus("Loading search error : " + f.getPath());
            logger.error("Error while loading search : {}", f, e);
        }
    }

    private void initSearch(SearchType type, ConditionType compareType, DataType dataType, long known) {
        searchService.setType(type);
        searchService.setCompareType(compareType);
        searchService.setDataType(dataType);
        searchService.setKnownValue(known);

        searchService.setOnSucceeded(v -> {
            result = (SearchResult) v.getSource().getValue();
            searchService.setPrevResult(result);
            mc.setStatus("Done! Total Result:" + result.size());
            resultList.clear();
            currentPage = 0;
            searchOptions.setDisable(false);
            if (result.getType() == SearchType.UNKNOWN) {
                searchConditionTypeDropdown.setValue(SearchType.PREVIOUS);
                searchConditionDropdown.setValue(ConditionType.NOT_EQUAL);
            }
            updatePageInfo();
        });

        searchService.setOnFailed(value -> {
            searchService.setPrevResult(null);
            mc.setStatus("Search failed!");
            Throwable t = value.getSource().getException();
            t.printStackTrace();
            logger.error("Search filaure", t);
            MainController.showMessage(t);
            searchOptions.setDisable(false);
        });
        searchOptions.setDisable(true);
        initService(searchService);
    }

    private void updatePageInfo() {
        int maxPages = result == null ? 0 : result.getPageCount();
        int size = result == null ? 0 : result.size();
        pageLeft.setDisable(currentPage == 0);
        pageRight.setDisable(currentPage >= maxPages - 1);
        if (maxPages == 0) {
            pageLabel.setText("Page: 0/0 (" + size + ")");
        } else {
            pageLabel.setText("Page: " + (currentPage + 1) + "/" + maxPages + " (" + size + ")");
        }
        resultList.clear();

        if (result != null) {
            List<Long> addrs = result.getPage(currentPage);
            for (int i = 0; i < addrs.size(); i++) {
                long addr = addrs.get(i);
                try {
                    long curr = result.getCurr(addr);
                    resultList.add(new SearchValueModel(addr, result.getPrev(addr), result.getCurr(addr)));
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    }

    private void initService(Service<?> service) {
        DoubleProperty p = mc.getProgressBar().progressProperty();
        p.unbind();
        p.bind(service.progressProperty());
        service.messageProperty().addListener((observable, oldValue, newValue) -> mc.setStatus(newValue));
        this.runningService = service;
        runningService.restart();
    }

    private boolean isServiceRunning() {
        return runningService != null && runningService.isRunning();
    }

    private DumpRegionSupplier getDumpRegionSupplier(Debugger conn) {
        if (result != null && result.size() > 0) {
            return DumpRegionSupplier.createSupplierFromRange(conn, result.getStart(), result.getEnd());
        }
        RangeType t = searchType.getValue();
        switch (t) {
            case RANGE:
                long start = searchStart.getValue();
                long end = searchEnd.getValue();
                return DumpRegionSupplier.createSupplierFromRange(conn, start, end);
            case MAIN_AND_HEAP:

                Range mainRange = mc.tools().searchWrtitableRange(EMemoryRegion.MAIN);
                Range heapRange = mc.tools().searchWrtitableRange(EMemoryRegion.HEAP);

                searchStart.getValueFactory().setValue(heapRange.getStart());
                searchEnd.getValueFactory().setValue(heapRange.getEnd());
                mainStart.getValueFactory().setValue(mainRange.getStart());
                mainsearchEnd.getValueFactory().setValue(mainRange.getEnd());

                long heapstart = searchStart.getValue();
                long heapend = searchEnd.getValue();
                long mainstart = mainStart.getValue();
                long mainend = mainsearchEnd.getValue();
                if (mainstart < heapstart) {
                    return DumpRegionSupplier.createSupplierFrom2Range(conn, mainstart, mainend, heapstart, heapend);
                } else {
                    return DumpRegionSupplier.createSupplierFrom2Range(conn, heapstart, heapend, mainstart, mainend);
                }
            case ALL:
                return DumpRegionSupplier.createSupplierFromInfo(conn, info -> info.isReadable() && info.isWriteable());
            case HEAP:
                return DumpRegionSupplier.createSupplierFromInfo(conn,
                        info -> info.isReadable() && info.getType() == MemoryType.HEAP);
            case TLS:
                return DumpRegionSupplier.createSupplierFromInfo(conn,
                        info -> info.isReadable() && info.getType() == MemoryType.THREAD_LOCAL);
        }
        return null;
    }
}
