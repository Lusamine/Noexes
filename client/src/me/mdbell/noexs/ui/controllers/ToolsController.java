package me.mdbell.noexs.ui.controllers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import me.mdbell.javafx.control.FormattedTableCell;
import me.mdbell.noexs.core.Debugger;
import me.mdbell.noexs.core.EMemoryRegion;
import me.mdbell.noexs.core.MemoryInfo;
import me.mdbell.noexs.core.MemoryType;
import me.mdbell.noexs.core.Result;
import me.mdbell.noexs.misc.ExpressionEvaluator;
import me.mdbell.noexs.ui.menus.MemoryInfoContextMenu;
import me.mdbell.noexs.ui.models.EAccessType;
import me.mdbell.noexs.ui.models.MemoryInfoTableModel;
import me.mdbell.noexs.ui.models.MemoryInfoUtils;
import me.mdbell.noexs.ui.models.Range;
import me.mdbell.util.HexUtils;

public class ToolsController implements IController {

    public TextField expression;
    public TextField expressionResult;
    private MainController mc;

    @FXML
    ListView<Long> pidList;
    @FXML
    TableView<MemoryInfoTableModel> memInfoTable;
    @FXML
    TableColumn<MemoryInfoTableModel, String> memInfoName;
    @FXML
    TableColumn<MemoryInfoTableModel, Number> memInfoAddr;
    @FXML
    TableColumn<MemoryInfoTableModel, Number> memInfoSize;
    @FXML
    TableColumn<MemoryInfoTableModel, MemoryType> memInfoType;
    @FXML
    TableColumn<MemoryInfoTableModel, Number> memInfoPerm;

    @FXML
    AnchorPane toolsTabPage;

    @FXML
    Label toolsTitleId;

    @FXML
    CheckBox autoFillTabs;

    private ObservableList<MemoryInfoTableModel> memoryInfoList;

    private final Map<String, Long> vars = new ConcurrentHashMap<>();

    private final ExpressionEvaluator evaluator = new ExpressionEvaluator(new ExpressionEvaluator.VariableProvider() {
        @Override
        public long get(String name) {
            return vars.get(name);
        }

        @Override
        public boolean containsVar(String value) {
            return vars.containsKey(value);
        }
    }, addr -> {
        Debugger debugger = mc.getDebugger();
        if (!debugger.connected()) {
            return 0;
        }
        return debugger.peek64(addr);
    });

    @FXML
    public void initialize() {
        memoryInfoList = FXCollections.observableArrayList();

        for (TableColumn c : memInfoTable.getColumns()) {
            c.setReorderable(false);
        }
        memInfoTable.setItems(new SortedList<>(memoryInfoList, (info1, info2) -> {
            long addr1 = info1.getAddr();
            long addr2 = info2.getAddr();
            if (addr1 < addr2) {
                return -1;
            }
            if (addr1 > addr2) {
                return 1;
            }
            return 0;
        }));

        memInfoName.setCellValueFactory(param -> param.getValue().nameProperty());
        memInfoAddr.setCellValueFactory(param -> param.getValue().addrProperty());
        memInfoSize.setCellValueFactory(param -> param.getValue().sizeProperty());
        memInfoType.setCellValueFactory(param -> param.getValue().typeProperty());
        memInfoPerm.setCellValueFactory(param -> param.getValue().accessProperty());

        memInfoAddr.setCellFactory(param -> new FormattedTableCell<>(addr -> HexUtils.formatAddress(addr.longValue())));
        memInfoSize.setCellFactory(param -> new FormattedTableCell<>(size -> HexUtils.formatSize(size.longValue())));
        memInfoPerm
                .setCellFactory(param -> new FormattedTableCell<>(access -> HexUtils.formatAccess(access.intValue())));

        pidList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            displayTitleId(mc.getDebugger().getTitleId(newValue));
        });

        MemoryInfoContextMenu cm = new MemoryInfoContextMenu(() -> mc, memInfoTable);
        memInfoTable.contextMenuProperty().setValue(cm);
    }

    public void setMainController(MainController c) {
        this.mc = c;
    }

    @Override
    public void onConnect() {
        toolsTabPage.setDisable(false);
        setPidsList();
        long attachedPid = mc.getDebugger().getAttachedPid();
        if (attachedPid != 0) {
            populateMemory();
            displayTitleId(mc.getDebugger().getTitleId(attachedPid));
        } else {
            memoryInfoList.clear();
        }
    }

    @Override
    public void onDisconnect() {
        toolsTabPage.setDisable(true);
    }

    public void setPidsList() {
        pidList.getItems().clear();
        Debugger debugger = mc.getDebugger();
        if (debugger.connected()) {
            for (long pid : debugger.getPids()) {
                pidList.getItems().add(pid);
            }
        } else {
            MainController.showMessage("You are not currently connected.", Alert.AlertType.WARNING);
        }
    }

    public void detachProcess() {
        Debugger conn = mc.getDebugger();
        if (conn.connected() && conn.attached()) {
            Result result = conn.detach();
            if (result.succeeded()) {
                mc.setTitle(null);
                mc.setStatus("Detached from process");
                memoryInfoList.clear();
            } else {
                MainController.showMessage("Detach failed.", result, Alert.AlertType.ERROR);
                mc.setStatus("Error");
            }
        } else {
            MainController.showMessage("Not attached.", Alert.AlertType.WARNING);
        }
    }

    public void attachProcess() {
        Debugger conn = mc.getDebugger();
        if (conn.connected()) {
            long selectedPid = pidList.getSelectionModel().getSelectedItem();

            if (selectedPid != 0) {
                boolean attachAlreadyDone = false;
                long attachedPid = conn.getAttachedPid();
                if (attachedPid != 0) {
                    if (attachedPid != selectedPid) {
                        conn.detach();
                    } else {
                        attachAlreadyDone = true;
                    }
                }

                boolean attachOk = true;
                if (!attachAlreadyDone) {
                    Result attached = conn.attach(selectedPid);
                    if (attached.failed()) {
                        attachOk = false;
                        MainController.showMessage("Attach failed.", attached, Alert.AlertType.ERROR);
                    }
                }

                if (attachOk) {

                    String status = "Attached to process pid : " + selectedPid;
                    if (attachAlreadyDone) {
                        status += " (Already attached)";
                    }

                    mc.setStatus(status);
                    mc.setTitle("Attached to: 0x" + HexUtils.formatTitleId(conn.getTitleId(selectedPid)));
                    populateMemory();

                }
            } else {
                MainController.showMessage("Invalid pid selected.", Alert.AlertType.ERROR);
            }
        } else {
            MainController.showMessage("You are not currently connected.", Alert.AlertType.WARNING);
        }
    }

    public void attachToCurrentProcess() {
        Debugger conn = mc.getDebugger();
        if (conn.connected()) {
            if (pidList.getItems().isEmpty()) {
                setPidsList();
            }

            Long currentPid = conn.getCurrentPid();
            pidList.getSelectionModel().select((Long) currentPid);

            displayTitleId(mc.getDebugger().getTitleId(currentPid));
            attachProcess();

            if (autoFillTabs.isSelected()) {
                setMainHeapAddressesInSearchTab(false);
                setMainFilterToPointerSearch();
            }

        } else {
            MainController.showMessage("You are not currently connected.", Alert.AlertType.WARNING);
        }
    }

    public void displayTitleId(long titleId) {
        Platform.runLater(() -> {
            toolsTitleId.setText("Title Id:" + (titleId == -1 ? "N/A" : HexUtils.formatTitleId(titleId)));
        });
    }

    public void updateMemoryInfo(MemoryInfo[] info) {
        memoryInfoList.clear();
        int moduleCount = 0;

        for (MemoryInfo m : info) {
            if (m.getType() == MemoryType.CODE_STATIC && m.getPerm() == 0b101) {
                moduleCount++;
            }
        }
        int mod = 0;
        boolean heap = false;
        EMemoryRegion region = null;
        for (MemoryInfo m : info) {
            String name = "-";
            if (m.getType() == MemoryType.HEAP && !heap) {
                heap = true;
                name = "heap";
                region = EMemoryRegion.HEAP;
            }
            if (m.getType() == MemoryType.HEAP) {
                region = EMemoryRegion.HEAP;
            } else if (region == EMemoryRegion.HEAP) {
                region = null;
            }
            if (m.getType() == MemoryType.CODE_STATIC && m.getPerm() == 0b101) {
                if (mod == 0 && moduleCount == 1) {
                    name = "main";
                    region = EMemoryRegion.MAIN;
                }
                if (moduleCount > 1) {
                    switch (mod) {
                        case 0:
                            name = "rtld";
                            region = EMemoryRegion.RTLD;
                            break;
                        case 1:
                            name = "main";
                            region = EMemoryRegion.MAIN;
                            break;
                        case 2:
                            name = "sdk";
                            region = EMemoryRegion.SDK;
                            break;
                        default:
                            name = "subsdk" + (mod - 2);
                            region = EMemoryRegion.SUBSDK;
                    }
                }
                mod++;
            }
            if (!name.equals("-")) {
                vars.put(name, m.getAddress());
            }
            if (m.getType() != MemoryType.UNMAPPED) {
                memoryInfoList.add(new MemoryInfoTableModel(name, m, region));
            }
        }
    }

    public EMemoryRegion getAddressMemoryRegion(long address) {
        return MemoryInfoUtils.getAddressMemoryRegion(address, memoryInfoList);
    }

    public long getOffset(long address, EMemoryRegion region) {
        return MemoryInfoUtils.getOffset(address, region, vars);
    }

    public ExpressionEvaluator getEvaluator() {
        return evaluator;
    }

    public void onParse(ActionEvent event) {
        try {
            String str = expression.getText();
            expressionResult.setText(HexUtils.formatAddress(evaluator.eval(str)));
        } catch (Exception e) {
            MainController.showMessage(e);
        }
    }

    public Range searchWrtitableRange(EMemoryRegion regionNameToSearch) {

        List<MemoryInfoTableModel> memInfos = memInfoTable.getItems();
        boolean inRegion = false;
        Long startAddress = null;
        Long stopAddress = null;
        for (MemoryInfoTableModel memInfo : memInfos) {
            String regionName = memInfo.nameProperty().getValue();
            if (StringUtils.startsWithIgnoreCase(regionName, regionNameToSearch.name())) {
                inRegion = true;
            } else if (inRegion && !StringUtils.equalsIgnoreCase(regionName, "-")) {
                inRegion = false;
                break;
            }

            boolean writeAccess = memInfo.hasAccess(EAccessType.WRITE);
            if (writeAccess) {
                if (inRegion && startAddress == null) {
                    startAddress = memInfo.getAddr();
                }
                if (inRegion && startAddress != null) {
                    stopAddress = memInfo.getEnd();
                }
            }
        }

        if (startAddress == null) {
            return null;
        }
        Range range = new Range(startAddress, stopAddress);
        return range;
    }

    public void populateMemory() {
        Debugger conn = mc.getDebugger();
        if (!conn.connected()) {
            return;
        }
        updateMemoryInfo(conn.query(0, 10000));
    }

    public void setMainHeapAddressesInSearchTab(boolean switchTab) {
        Range range = searchWrtitableRange(EMemoryRegion.MAIN);
        if (range != null) {
            mc.search().mainsetSearchRange(range.getStart(), range.getEnd());
        }
        Range range2 = searchWrtitableRange(EMemoryRegion.HEAP);
        if (range != null) {
            mc.search().setSearchRange(range2.getStart(), range2.getEnd());
        }
        if (switchTab) {
            mc.setTab(MainController.Tab.SEARCH);
        }
    }

    public void setMainFilterToPointerSearch() {
        Range range = searchWrtitableRange(EMemoryRegion.MAIN);
        if (range != null) {
            mc.pointer().setFilterMin(range.getStart());
            mc.pointer().setFilterMax(range.getEnd());
            mc.pointer().setFilterActivated(true);
        }
    }
}
