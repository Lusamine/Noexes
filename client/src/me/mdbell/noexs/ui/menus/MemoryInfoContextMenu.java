package me.mdbell.noexs.ui.menus;

import java.util.function.Supplier;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import me.mdbell.noexs.core.EMemoryRegion;
import me.mdbell.noexs.ui.controllers.MainController;
import me.mdbell.noexs.ui.models.MemoryInfoTableModel;
import me.mdbell.noexs.ui.models.Range;

public class MemoryInfoContextMenu extends ContextMenu {

    public MemoryInfoContextMenu(Supplier<MainController> mc, TableView<MemoryInfoTableModel> memInfoTable) {
        MenuItem searchBoth = new MenuItem("Search (Start & End)");
        MenuItem searchStart = new MenuItem("Search(Start)");
        MenuItem searchEnd = new MenuItem("Search (End)");
        MenuItem mainsearchAuto = new MenuItem("Search Auto Main");
        MenuItem heapsearchAuto = new MenuItem("Search Auto Heap");
        MenuItem mainHeapSearchAuto = new MenuItem("Search Auto Main + Heap");
        MenuItem mainsearchBoth = new MenuItem("Main Search (Start & End)");
        MenuItem mainsearchStart = new MenuItem("Main Search(Start)");
        MenuItem mainsearchEnd = new MenuItem("Main Search (End)");
        MenuItem ptrMainAuto = new MenuItem("Pointer Search Auto Main");
        MenuItem ptrMain = new MenuItem("Pointer Search (Main)");
        MenuItem ptrFilter = new MenuItem("Pointer Search (Filter Min & Max)");
        MenuItem ptrFilterStart = new MenuItem("Pointer Search (Filter Min)");
        MenuItem ptrFilterEnd = new MenuItem("Pointer Search (Filter Max)");
        MenuItem memoryView = new MenuItem("Memory Viewer");
        MenuItem disassembler = new MenuItem("Disassembler");
        searchBoth.setOnAction(event -> {
            MemoryInfoTableModel model = memInfoTable.getSelectionModel().getSelectedItem();
            if (model == null) {
                return;
            }
            mc.get().search().setSearchRange(model.getAddr(), model.getEnd());
            mc.get().setTab(MainController.Tab.SEARCH);
        });
        searchStart.setOnAction(event -> {
            MemoryInfoTableModel model = memInfoTable.getSelectionModel().getSelectedItem();
            if (model == null) {
                return;
            }
            mc.get().search().setStart(model.getAddr());
        });

        mainsearchAuto.setOnAction(event -> {
            Range range = mc.get().tools().searchWrtitableRange(EMemoryRegion.MAIN);
            if (range != null) {
                mc.get().search().setSearchRange(range.getStart(), range.getEnd());
            }
        });

        ptrMainAuto.setOnAction(event -> {
            mc.get().tools().setMainFilterToPointerSearch();
        });

        heapsearchAuto.setOnAction(event -> {
            Range range = mc.get().tools().searchWrtitableRange(EMemoryRegion.HEAP);
            if (range != null) {
                mc.get().search().setSearchRange(range.getStart(), range.getEnd());
            }
            mc.get().setTab(MainController.Tab.SEARCH);
        });

        mainHeapSearchAuto.setOnAction(event -> {
            mc.get().tools().setMainHeapAddressesInSearchTab(true);
        });

        mainsearchBoth.setOnAction(event -> {
            MemoryInfoTableModel model = memInfoTable.getSelectionModel().getSelectedItem();
            if (model == null) {
                return;
            }
            mc.get().search().mainsetSearchRange(model.getAddr(), model.getEnd());
        });
        mainsearchStart.setOnAction(event -> {
            MemoryInfoTableModel model = memInfoTable.getSelectionModel().getSelectedItem();
            if (model == null) {
                return;
            }
            mc.get().search().mainsetStart(model.getAddr());
        });
        mainsearchEnd.setOnAction(event -> {
            MemoryInfoTableModel model = memInfoTable.getSelectionModel().getSelectedItem();
            if (model == null) {
                return;
            }
            mc.get().search().mainsetEnd(model.getEnd());
        });

        ptrMain.setOnAction(event -> {
            MemoryInfoTableModel model = memInfoTable.getSelectionModel().getSelectedItem();
            if (model == null) {
                return;
            }
            mc.get().pointer().setRelativeAddress(model.getAddr());
        });

        ptrFilter.setOnAction(event -> {
            MemoryInfoTableModel model = memInfoTable.getSelectionModel().getSelectedItem();
            if (model == null) {
                return;
            }
            mc.get().pointer().setFilterMin(model.getAddr());
            mc.get().pointer().setFilterMax(model.getEnd());
            mc.get().setTab(MainController.Tab.POINTER_SEARCH);
        });

        ptrFilterStart.setOnAction(event -> {
            MemoryInfoTableModel model = memInfoTable.getSelectionModel().getSelectedItem();
            if (model == null) {
                return;
            }
            mc.get().pointer().setFilterMin(model.getAddr());
        });

        ptrFilterEnd.setOnAction(event -> {
            MemoryInfoTableModel model = memInfoTable.getSelectionModel().getSelectedItem();
            if (model == null) {
                return;
            }
            mc.get().pointer().setFilterMax(model.getEnd());
        });

        searchEnd.setOnAction(event -> {
            MemoryInfoTableModel model = memInfoTable.getSelectionModel().getSelectedItem();
            if (model == null) {
                return;
            }
            mc.get().search().setEnd(model.getEnd());
        });
        memoryView.setOnAction(event -> {
            MemoryInfoTableModel model = memInfoTable.getSelectionModel().getSelectedItem();
            if (model == null) {
                return;
            }
            mc.get().memory().setViewAddress(model.getAddr());
            mc.get().setTab(MainController.Tab.MEMORY_VIEWER);

        });
        disassembler.setOnAction(event -> {
            MemoryInfoTableModel model = memInfoTable.getSelectionModel().getSelectedItem();
            if (model == null) {
                return;
            }
            mc.get().disassembly().setDisassembleAddress(model.getAddr());
            mc.get().setTab(MainController.Tab.DISASSEMBLER);
        });
        getItems().addAll(searchBoth, searchStart, searchEnd, mainsearchAuto, heapsearchAuto, mainHeapSearchAuto,
                mainsearchStart, mainsearchEnd, ptrMainAuto, ptrMain, ptrFilter, ptrFilterStart, ptrFilterEnd,
                memoryView, disassembler);
    }

}
