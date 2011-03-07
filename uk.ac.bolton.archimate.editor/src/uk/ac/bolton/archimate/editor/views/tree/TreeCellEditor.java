/*******************************************************************************
 * Copyright (c) 2011 Bolton University, UK.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 *******************************************************************************/
package uk.ac.bolton.archimate.editor.views.tree;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.TreeEditor;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import uk.ac.bolton.archimate.editor.utils.PlatformUtils;
import uk.ac.bolton.archimate.editor.views.tree.commands.RenameCommandHandler;
import uk.ac.bolton.archimate.model.INameable;


/**
 * Tree Cell Editor
 * This is basically Snippet111 from http://www.eclipse.org/swt/snippets/ (unattributed author)
 * 
 * @author Phillip Beauvoir
 */
public class TreeCellEditor {
    
    private Tree fTree;
    
    // Track last item if we are allowing edit on click
    private TreeItem fLastItem;
    
    private TreeItem fCurrentItem;
    
    private boolean showBorder = true;
    private TreeEditor fEditor;
    private Composite fComposite;
    private Text fText;
    
    private INameable fElement;
    
    private String fOldText;
    
    private boolean EDIT_ON_CLICK = false;
    
    public TreeCellEditor(Tree tree) {
        fTree = tree;
        fEditor = new TreeEditor(fTree);
       
        // Mac Cocoa Context Menu doesn't send FocusOut
        if(PlatformUtils.isMacCocoa()) {
            fTree.addListener(SWT.MenuDetect, new Listener() {
                @Override
                public void handleEvent(Event event) {
                    finaliseEdit();
                }
            });
        }
        
        // There's just too many problems with this
        if(EDIT_ON_CLICK) {
            boolean USE_SELECTION_EVENT = false;
            
            // Use Selection event.
            // On Cocoa a Selection event causes a a badly timed FocusOut event (like MouseDown)
            // On Linux Ubuntu pressing a letter key fires a selection event and selects a new tree node
            // Not good.
            if(USE_SELECTION_EVENT) {
                fTree.addListener(SWT.Selection, new Listener() {
                    @Override
                    public void handleEvent(Event event) {
                        _editItem((TreeItem)event.item);
                    }
                });
            }
            // Use MouseUp event.
            // A MouseDown fires a badly timed FocusOut event on Cocoa.
            // And a double-click also activates it....so it's not good.
            else {
                fTree.addListener(SWT.MouseUp, new Listener() {
                    @Override
                    public void handleEvent(Event event) {
                        if(event.button == 1) {
                            TreeItem item = fTree.getItem(new Point(event.x, event.y));
                            _editItem(item);
                        }
                    }
                });
            }
        }
    }
    
    /**
     * Edit a tree item in-place
     * @param item
     */
    public void editItem(TreeItem item) {
        if(isEditing()) {
            return;
        }
        
        fLastItem = item; // Ensure we are convinced
        _editItem(item);
    }
    
    private void _editItem(final TreeItem item) {
        // Safety check (really needed for Mac Cocoa)
        finaliseEdit();
        
        if(item != null && item == fLastItem && RenameCommandHandler.canRename(item.getData())) {
            fElement = (INameable)item.getData();
            
            fOldText = item.getText();
            fCurrentItem = item;
            
            fComposite = new Composite(fTree, SWT.NONE);
            if(showBorder) {
                fComposite.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
            }
            
            fText = new Text(fComposite, SWT.NONE);
            
            final int inset = showBorder ? 1 : 0;
            
            fComposite.addListener(SWT.Resize, new Listener() {
                public void handleEvent(Event e) {
                    Rectangle rect = fComposite.getClientArea();
                    fText.setBounds(rect.x + inset, rect.y + inset, rect.width - inset * 2, rect.height - inset * 2);
                }
            });
            
            Listener textListener = new Listener() {
                @Override
                public void handleEvent(Event event) {
                    switch(event.type) {
                        case SWT.FocusOut:
                            finaliseEdit();
                            break;

                        case SWT.Verify:
                            String newText = fText.getText();
                            String leftText = newText.substring(0, event.start);
                            String rightText = newText.substring(event.end, newText.length());
                            GC gc = new GC(fText);
                            Point size = gc.textExtent(leftText + event.text + rightText);
                            gc.dispose();
                            size = fText.computeSize(size.x, SWT.DEFAULT);
                            fEditor.horizontalAlignment = SWT.LEFT;
                            //Rectangle itemRect = item.getBounds(), rect = fTree.getClientArea();
                            //fEditor.minimumWidth = Math.max(size.x, itemRect.width) + inset * 2;
                            //int left = itemRect.x, right = rect.x + rect.width;
                            //fEditor.minimumWidth = Math.min(fEditor.minimumWidth, right - left);
                            fEditor.minimumWidth = size.x + 20; // Added this
                            fEditor.minimumHeight = size.y + inset * 2; 
                            fEditor.layout();
                            break;
                            
                        case SWT.Traverse:
                            switch(event.detail) {
                                case SWT.TRAVERSE_RETURN:
                                    finaliseEdit();
                                    event.doit = false;
                                    break;
                                    
                                case SWT.TRAVERSE_ESCAPE:
                                    cancelEditing();
                                    event.doit = false;
                                    break;
                            }
                            break;
                    }
                }
            };
            
            fText.addListener(SWT.FocusOut, textListener);
            fText.addListener(SWT.Traverse, textListener);
            fText.addListener(SWT.Verify, textListener);
            
            fEditor.setEditor(fComposite, item);
            
            fText.setText(fElement.getName());
            fText.selectAll();
            fText.setFocus();

            // Clear item
            item.setText("");
        }
        
        // Store last item even if null
        fLastItem = item;
    }
    
    private void finaliseEdit() {
        if(isEditing()) {
            String updatedText = fText.getText();
            if(!updatedText.equals(fElement.getName())) {
                disposeEditor();
                RenameCommandHandler.doRenameCommand(fElement, updatedText);
            }
            else {
                cancelEditing();
            }
        }
    }
    
    private boolean isEditing() {
        return fText != null && !fText.isDisposed();
    }

    public void cancelEditing() {
        disposeEditor();
        
        if(fCurrentItem != null && !fCurrentItem.isDisposed()) {
            fCurrentItem.setText(fOldText);
            fCurrentItem = null;
        }
    }
    
    private void disposeEditor() {
        if(fComposite != null && !fComposite.isDisposed()) {
            fComposite.dispose();
            fComposite = null;
            fText = null;
        }
        
        if(fEditor != null) {
            fEditor.setEditor(null, null);
        }
    }
}