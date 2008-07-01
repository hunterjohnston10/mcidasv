package edu.wisc.ssec.mcidasv.ui;

import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JTabbedPane;

import org.w3c.dom.Element;

import edu.wisc.ssec.mcidasv.Constants;

import ucar.unidata.idv.IntegratedDataViewer;
import ucar.unidata.idv.ui.IdvWindow;
import ucar.unidata.ui.ComponentGroup;
import ucar.unidata.ui.ComponentHolder;
import ucar.unidata.xml.XmlUtil;

/**
 * This is a rather simplistic drag and drop enabled JTabbedPane. It allows
 * users to use drag and drop to move tabs between windows and reorder tabs.
 * 
 * Jeff's work in DnDTree was a great inspiration. :)
 */
public class DraggableTabbedPane extends JTabbedPane 
	implements DragGestureListener, DragSourceListener, DropTargetListener {

    private static final long serialVersionUID = -5710302260509445686L;

	/** Local shorthand for the actions we're accepting. */
	private static final int VALID_ACTION = DnDConstants.ACTION_COPY_OR_MOVE;

	/** Path to the icon we'll use as an index indicator. */
	private static final String IDX_ICON = 
		"/edu/wisc/ssec/mcidasv/resources/icons/tabmenu/go-down.png";

	/** 
	 * Used to signal across all DraggableTabbedPanes that the component 
	 * currently being dragged originated in another window. This'll let McV
	 * determine if it has to do a quiet ComponentHolder transfer.
	 */
	protected static boolean outsideDrag = false;

	/** The actual image that we'll use to display the index indications. */
	private final Image INDICATOR = 
		(new ImageIcon(getClass().getResource(IDX_ICON))).getImage();

	/** The tab index where the drag started. */
	private int sourceIndex = -1;

	/** The tab index that the user is currently over. */
	private int overIndex = -1;

	/** Used for starting the dragging process. */
	private DragSource dragSource;

	/** Used for signaling that we'll accept drops (registers listeners). */
	private DropTarget dropTarget;

	/** The component group holding our components. */
	private McvComponentGroup group;

	/** The IDV window that contains this tabbed pane. */
	private IdvWindow window;

	/** Keep around this reference so that we can access the UI Manager. */
	private IntegratedDataViewer idv;

	/**
	 * Mostly just registers that this component should listen for drag and
	 * drop operations.
	 * 
	 * @param win The IDV window containing this tabbed pane.
	 * @param idv The main IDV instance.
	 * @param group The <tt>ComponentGroup</tt> that holds this component's tabs.
	 */
	public DraggableTabbedPane(IdvWindow win, IntegratedDataViewer idv, McvComponentGroup group) {
		dropTarget = new DropTarget(this, this);
		dragSource = new DragSource();
		dragSource.createDefaultDragGestureRecognizer(this, VALID_ACTION, this);

		this.group = group;
		this.idv = idv;
		window = win;
	}

	/**
	 * Triggered when the user does a (platform-dependent) drag initiating 
	 * gesture. Used to populate the things that the user is attempting to 
	 * drag. 
	 */
	public void dragGestureRecognized(DragGestureEvent e) {
		sourceIndex = getSelectedIndex();

		// transferable allows us to store the current DraggableTabbedPane and
		// the source index of the drag inside the various drag and drop event
		// listeners.
		Transferable transferable = new TransferableIndex(this, sourceIndex);

		Cursor cursor = DragSource.DefaultMoveDrop;
		if (e.getDragAction() != DnDConstants.ACTION_MOVE)
			cursor = DragSource.DefaultCopyDrop;

		dragSource.startDrag(e, cursor, transferable, this);
	}

	/** 
	 * Triggered when the user drags into <tt>dropTarget</tt>.
	 */
	public void dragEnter(DropTargetDragEvent e) {
		DataFlavor[] flave = e.getCurrentDataFlavors();
		if ((flave.length == 0) || !(flave[0] instanceof DraggableTabFlavor))
			return;

		//System.out.print("entered window outsideDrag=" + outsideDrag + " sourceIndex=" + sourceIndex);

		// if the DraggableTabbedPane associated with this drag isn't the 
		// "current" DraggableTabbedPane we're dealing with a drag from another
		// window and we need to make this DraggableTabbedPane aware of that.
		if (((DraggableTabFlavor)flave[0]).getDragTab() != this) {
			//System.out.println(" coming from outside!");
			outsideDrag = true;
		} else {
			//System.out.println(" re-entered parent window");
			outsideDrag = false;
		}
	}

	/**
	 * Triggered when the user drags out of <tt>dropTarget</tt>.
	 */
	public void dragExit(DropTargetEvent e) {
//		System.out.println("drag left a window outsideDrag=" + outsideDrag + " sourceIndex=" + sourceIndex);
		overIndex = -1;

		//outsideDrag = true;
		repaint();
	}

	/**
	 * Triggered continually while the user is dragging over 
	 * <tt>dropTarget</tt>. McIDAS-V uses this to draw the index indicator.
	 * 
	 * @param e Information about the current state of the drag.
	 */
	public void dragOver(DropTargetDragEvent e) {
//		System.out.println("dragOver outsideDrag=" + outsideDrag + " sourceIndex=" + sourceIndex);
		if ((!outsideDrag) && (sourceIndex == -1))
			return;

		Point dropPoint = e.getLocation();
		overIndex = indexAtLocation(dropPoint.x, dropPoint.y);

		repaint();
	}

	/**
	 * Triggered when a drop has happened over <tt>dropTarget</tt>.
	 * 
	 * @param e State that we'll need in order to handle the drop.
	 */
	public void drop(DropTargetDropEvent e) {
		// if the dragged ComponentHolder was dragged from another window we
		// must do a behind-the-scenes transfer from its old ComponentGroup to 
		// the end of the new ComponentGroup.
		if (outsideDrag) {
			DataFlavor[] flave = e.getCurrentDataFlavors();
			DraggableTabbedPane other = ((DraggableTabFlavor)flave[0]).getDragTab();

			ComponentHolder target = other.removeDragged();
			sourceIndex = group.quietAddComponent(target);
			outsideDrag = false;
		}

		// check to see if we've actually dropped something McV understands.
		if (sourceIndex >= 0) {
			e.acceptDrop(VALID_ACTION);
			Point dropPoint = e.getLocation();
			int dropIndex = indexAtLocation(dropPoint.x, dropPoint.y);

			// make sure the user chose to drop over a valid area/thing first
			// then do the actual drop.
			if ((dropIndex != -1) && (getComponentAt(dropIndex) != null))
				doDrop(sourceIndex, dropIndex);

			// clean up anything associated with the current drag and drop
			e.getDropTargetContext().dropComplete(true);
			sourceIndex = -1;
			overIndex = -1;

			repaint();
		}
	}

	/**
	 * &quot;Quietly&quot; removes the dragged component from its group. If the
	 * last component in a group has been dragged out of the group, the 
	 * associated window will be killed.
	 * 
	 * @return The removed component.
	 */
	private ComponentHolder removeDragged() {
		ComponentHolder removed = group.quietRemoveComponentAt(sourceIndex);

		// no point in keeping an empty window around.
		List<ComponentHolder> comps = group.getDisplayComponents();
//		if ((window != null) && (comps == null || comps.isEmpty()))
		if (comps == null || comps.isEmpty())
			window.dispose();

		return removed;
	}

	/**
	 * Moves a component to its new index within the component group.
	 * 
	 * @param srcIdx The old index of the component.
	 * @param dstIdx The new index of the component.
	 */
	public void doDrop(int srcIdx, int dstIdx) {
		List<ComponentHolder> comps = group.getDisplayComponents();
		ComponentHolder src = comps.get(srcIdx);

		group.removeComponent(src);
		group.addComponent(src, dstIdx);
	}

	/**
	 * Overridden so that McV can draw an indicator of a dragged tab's possible
	 * new position.
	 */
	@Override
	public void paint(Graphics g) {
		super.paint(g);

		if (overIndex == -1)
			return;

		Rectangle bounds = getBoundsAt(overIndex);

		if (bounds != null)
			g.drawImage(INDICATOR, bounds.x-7, bounds.y, null);
	}

	/**
	 * Used to simply provide a reference to the originating 
	 * DraggableTabbedPane while we're dragging and dropping.
	 */
	private static class TransferableIndex implements Transferable {
		private DraggableTabbedPane tabbedPane;

		private int index;

		public TransferableIndex(DraggableTabbedPane dt, int i) {
			tabbedPane = dt;
			index = i;
		}

		// whatever is returned here needs to be serializable. so we can't just
		// return the tabbedPane. :(
		public Object getTransferData(DataFlavor flavor) {
			return index;
		}

		public DataFlavor[] getTransferDataFlavors() {
			return new DataFlavor[] { new DraggableTabFlavor(tabbedPane) };
		}

		public boolean isDataFlavorSupported(DataFlavor flavor) {
			return true;
		}
	}

	/**
	 * To be perfectly honest I'm still a bit fuzzy about DataFlavors. As far 
	 * as I can tell they're used like so: if a user dragged an image file on
	 * to a toolbar, the toolbar might be smart enough to add the image. If the
	 * user dragged the same image file into a text document, the text editor
	 * might be smart enough to insert the path to the image or something.
	 * 
	 * I'm thinking that would require two data flavors: some sort of toolbar
	 * flavor and then some sort of text flavor?
	 */
	private static class DraggableTabFlavor extends DataFlavor {
		private DraggableTabbedPane tabbedPane;

		public DraggableTabFlavor(DraggableTabbedPane dt) {
			super(DraggableTabbedPane.class, "DraggableTabbedPane");
			tabbedPane = dt;
		}

		public DraggableTabbedPane getDragTab() {
			return tabbedPane;
		}
	}

	/**
	 * Handle the user dropping a tab outside of a McV window. This will create
	 * a new window and add the dragged tab to the ComponentGroup within the
	 * newly created window. The new window is the same size as the origin 
	 * window, with the top centered over the location where the user released
	 * the mouse.
	 * 
	 * @param dragged The ComponentHolder that's being dragged around.
	 * @param drop The x- and y-coordinates where the user dropped the tab.
	 */
	private void newWindowDrag(ComponentHolder dragged, Point drop) {
//		if ((dragged == null) || (window == null))
	    if (dragged == null)
			return;

		UIManager ui = (UIManager)idv.getIdvUIManager();

		try {
			Element skinRoot = XmlUtil.getRoot(Constants.BLANK_COMP_GROUP, getClass());

			// create the new window with visibility off, so we can position 
			// the window in a sensible way before the user has to see it.
			IdvWindow w = ui.createNewWindow(null, false, "McIDAS-V", 
											 Constants.BLANK_COMP_GROUP, 
											 skinRoot, false, null);

			// make the new window the same size as the old and center the 
			// *top* of the window over the drop point.
			int height = window.getBounds().height;
			int width = window.getBounds().width;
			int startX = drop.x - (width / 2);

			w.setBounds(new Rectangle(startX, drop.y, width, height));

			// be sure to add the dragged component holder to the new window.
			ComponentGroup newGroup = 
				(ComponentGroup)w.getComponentGroups().get(0);

			newGroup.addComponent(dragged);

			// let there be a window
			w.setVisible(true);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	/**
	 * Handles what happens at the very end of a drag and drop. Since I could
	 * not find a better method for it, tabs that are dropped outside of a McV
	 * window are handled with this method.
	 */
	public void dragDropEnd(DragSourceDropEvent e) {
		//System.out.println("other dragDropEnd outsideDrag=" + outsideDrag + " sourceIndex=" + sourceIndex + " success=" + e.getDropSuccess() + " action=" + e.getDropAction());
		if (!e.getDropSuccess() && e.getDropAction() == 0) {
			newWindowDrag(removeDragged(), e.getLocation());
		}
	}

	// required methods that we don't need to implement yet.
	public void dragEnter(DragSourceDragEvent e) {
		//System.out.println("other dragEnter outsideDrag=" + outsideDrag + " sourceIndex=" + sourceIndex);
	}
	public void dragExit(DragSourceEvent e) {
		//System.out.println("other dragExit outsideDrag=" + outsideDrag + " sourceIndex=" + sourceIndex);
	}
	public void dragOver(DragSourceDragEvent e) {
		//System.out.println("other dragOver outsideDrag=" + outsideDrag + " sourceIndex=" + sourceIndex);
	}
	public void dropActionChanged(DragSourceDragEvent e) {
		//System.out.println("other dropActionChanged outsideDrag=" + outsideDrag + " sourceIndex=" + sourceIndex);
	}
	public void dropActionChanged(DropTargetDragEvent e) {
		//System.out.println("other dropActionChanged outsideDrag=" + outsideDrag + " sourceIndex=" + sourceIndex);
	}
}
