/*
 [The "BSD license"]
 Copyright (c) 2011 Terence Parr
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
 3. The name of the author may not be used to endorse or promote products
    derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.antlr.v4.runtime.tree;

import org.antlr.v4.runtime.*;

import java.util.*;

/** A TreeAdaptor that works with any Tree implementation. */
public abstract class BaseTreeAdaptor implements TreeAdaptor {
	/** System.identityHashCode() is not always unique; we have to
	 *  track ourselves.  That's ok, it's only for debugging, though it's
	 *  expensive: we have to create a hashtable with all tree nodes in it.
	 */
	protected Map treeToUniqueIDMap;
	protected int uniqueNodeID = 1;

	// BEGIN v4 stuff

	/* Upon B^ in these cases:
		A B^	add kids to newRoot B
				return B
		A^ B^	add any remaining kids to A
				clear kids
				add A as child of B
				return B
		r B^	add kids to B
				clear kids
				return B
		r^ B^

	   Upon r^ in these cases:
	    A r^
	    A^ r^
	    s r^
	    s^ r^

	    A r^ B, r is (C D). result = (C D A B)
	    becomeRoot((C D), (nil A))

	    A r^ B, r is (nil C D). result = (A C D B)
	    becomeRoot((nil C D), (nil A))

todo: ref to r must chk if ^(nil list); can't just add to List<Tree>
	 */
	public Object becomeRoot(Object oldRoot, Object newRoot, List kids) {
		// old root can be nil rooted if root set by r^ call like "r^ A"; implies a list
		if ( ((Tree)oldRoot).isNil() ) {
			addChildren(newRoot, getChildren(oldRoot)); // add old children to new root
			addChildren(newRoot, kids);					// add unattached kids also
			kids.clear();
			return oldRoot;
		}
		// must be from previous root match like "A^ B^" or single node
		// result from r^ or rooted/non-nil root from r^ in "r^ A^".
		addChildren(oldRoot, kids);
		kids.clear();
		addChild(newRoot, oldRoot); // newRoot becomes root of old root
		return newRoot;
	}

	/** if oldRoot is nil then (oldRoot must be r^ result):
	 * 		create newRoot for rootToken
	 *  	add kids to newRoot
	 *   	clear kids
	 *  	return as new root
	 *  if oldRoot not nil then (must have created from A^):
	 *  	add kids to oldRoot
	 *  	clear kids
	 *  	create rootToken
	 *  	return as new root
	 */
	public Object becomeRoot(Object oldRoot, Token rootToken, List kids) {

		// old root can be nil rooted if root set by r^ call like "r^ A"; implies a list
		if ( ((Tree)oldRoot).isNil() ) {
			Tree newRoot = (Tree)create(rootToken);
			addChildren(newRoot, getChildren(oldRoot)); // add old children to new root
			addChildren(newRoot, kids);					// add unattached kids also
			kids.clear();
			return oldRoot;
		}
		// must be from previous root match like "A^ B^" or single node
		// result from r^ or rooted/non-nil root from r^ in "r^ A^".
		addChildren(oldRoot, kids);
		kids.clear();
		Object newRoot = create(rootToken);
		addChild(newRoot, oldRoot); // newRoot becomes root of old root
		return newRoot;
	}

	public void addChildren(Object root, List kids) {
		if ( root!=null ) ((Tree)root).addChildren(kids);
	}

	public List getChildren(Object root) { return ((Tree)root).getChildren(); }

	/** Add remaining kids to non-null root.  If null root, add kids to
	 *  new nil root.  If only one kid, make it the root.  Return the
	 *  root.
	 */
	public Object rulePostProcessing(Object root, List kids) {
		if ( root!=null ) {
			addChildren(root, kids);
		}
		else {
			if ( kids.size()==1 ) {
				root = kids.get(0);
				// whoever invokes rule will set parent and child index
				((Tree)root).setParent(null);
				((Tree)root).setChildIndex(-1);
			}
			if ( kids.size()>1 ) {
				root = nil();
				addChildren(root, kids);
			}
		}
		return root;
	}

	// END v4 stuff

	public Object nil() {
		return create(null);
	}

	/** create tree node that holds the start and stop tokens associated
	 *  with an error.
	 *
	 *  If you specify your own kind of tree nodes, you will likely have to
	 *  override this method. CommonTree returns Token.INVALID_TOKEN_TYPE
	 *  if no token payload but you might have to set token type for diff
	 *  node type.
     *
     *  You don't have to subclass CommonErrorNode; you will likely need to
     *  subclass your own tree node class to avoid class cast exception.
	 */
	public Object errorNode(TokenStream input, Token start, Token stop,
							RecognitionException e)
	{
		CommonErrorNode t = new CommonErrorNode(input, start, stop, e);
		//System.out.println("returning error node '"+t+"' @index="+input.index());
		return t;
	}

	public boolean isNil(Object tree) {
		return ((Tree)tree).isNil();
	}

	public Object dupTree(Object tree) {
		return dupTree(tree, null);
	}

	/** This is generic in the sense that it will work with any kind of
	 *  tree (not just Tree interface).  It invokes the adaptor routines
	 *  not the tree node routines to do the construction.
	 */
	public Object dupTree(Object t, Object parent) {
		if ( t==null ) {
			return null;
		}
		Object newTree = dupNode(t);
		// ensure new subtree root has parent/child index set
		setChildIndex(newTree, getChildIndex(t)); // same index in new tree
		setParent(newTree, parent);
		int n = getChildCount(t);
		for (int i = 0; i < n; i++) {
			Object child = getChild(t, i);
			Object newSubTree = dupTree(child, t);
			addChild(newTree, newSubTree);
		}
		return newTree;
	}

	/** Add a child to the tree t.  If child is a flat tree (a list), make all
	 *  in list children of t.  Warning: if t has no children, but child does
	 *  and child isNil then you can decide it is ok to move children to t via
	 *  t.children = child.children; i.e., without copying the array.  Just
	 *  make sure that this is consistent with have the user will build
	 *  ASTs.
	 */
	public void addChild(Object t, Object child) {
		if ( t!=null && child!=null ) {
			((Tree)t).addChild((Tree)child);
		}
	}

	/** If oldRoot is a nil root, just copy or move the children to newRoot.
	 *  If not a nil root, make oldRoot a child of newRoot.
	 *
	 *    old=^(nil a b c), new=r yields ^(r a b c)
	 *    old=^(a b c), new=r yields ^(r ^(a b c))
	 *
	 *  If newRoot is a nil-rooted single child tree, use the single
	 *  child as the new root node.
	 *
	 *    old=^(nil a b c), new=^(nil r) yields ^(r a b c)
	 *    old=^(a b c), new=^(nil r) yields ^(r ^(a b c))
	 *
	 *  If oldRoot was null, it's ok, just return newRoot (even if isNil).
	 *
	 *    old=null, new=r yields r
	 *    old=null, new=^(nil r) yields ^(nil r)
	 *
	 *  Return newRoot.  Throw an exception if newRoot is not a
	 *  simple node or nil root with a single child node--it must be a root
	 *  node.  If newRoot is ^(nil x) return x as newRoot.
	 *
	 *  Be advised that it's ok for newRoot to point at oldRoot's
	 *  children; i.e., you don't have to copy the list.  We are
	 *  constructing these nodes so we should have this control for
	 *  efficiency.
	 */
	public Object becomeRoot(Object newRoot, Object oldRoot) {
        //System.out.println("becomeroot new "+newRoot.toString()+" old "+oldRoot);
        Tree newRootTree = (Tree)newRoot;
		Tree oldRootTree = (Tree)oldRoot;
		if ( oldRoot==null ) {
			return newRoot;
		}
		// handle ^(nil real-node)
		if ( newRootTree.isNil() ) {
            int nc = newRootTree.getChildCount();
            if ( nc==1 ) newRootTree = (Tree)newRootTree.getChild(0);
            else if ( nc >1 ) {
				// TODO: make tree run time exceptions hierarchy
				throw new RuntimeException("more than one node as root (TODO: make exception hierarchy)");
			}
        }
		// add oldRoot to newRoot; addChild takes care of case where oldRoot
		// is a flat list (i.e., nil-rooted tree).  All children of oldRoot
		// are added to newRoot.
		newRootTree.addChild(oldRootTree);
		return newRootTree;
	}

	/** Transform ^(nil x) to x and nil to null */
	public Object rulePostProcessing(Object root) {
		//System.out.println("rulePostProcessing: "+((Tree)root).toStringTree());
		Tree r = (Tree)root;
		if ( r!=null && r.isNil() ) {
			if ( r.getChildCount()==0 ) {
				r = null;
			}
			else if ( r.getChildCount()==1 ) {
				r = (Tree)r.getChild(0);
				// whoever invokes rule will set parent and child index
				r.setParent(null);
				r.setChildIndex(-1);
			}
		}
		return r;
	}

	public Object becomeRoot(Token newRoot, Object oldRoot) {
		return becomeRoot(create(newRoot), oldRoot);
	}

	public Object create(int tokenType, Token fromToken) {
		fromToken = createToken(fromToken);
		//((ClassicToken)fromToken).setType(tokenType);
		fromToken.setType(tokenType);
		Tree t = (Tree)create(fromToken);
		return t;
	}

	public Object create(int tokenType, Token fromToken, String text) {
        if (fromToken == null) return create(tokenType, text);
		fromToken = createToken(fromToken);
		fromToken.setType(tokenType);
		fromToken.setText(text);
		Tree t = (Tree)create(fromToken);
		return t;
	}

	public Object create(int tokenType, String text) {
		Token fromToken = createToken(tokenType, text);
		Tree t = (Tree)create(fromToken);
		return t;
	}

	public int getType(Object t) {
		return ((Tree)t).getType();
	}

	public void setType(Object t, int type) {
		throw new NoSuchMethodError("don't know enough about Tree node");
	}

	public String getText(Object t) {
		return ((Tree)t).getText();
	}

	public void setText(Object t, String text) {
		throw new NoSuchMethodError("don't know enough about Tree node");
	}

	public Object getChild(Object t, int i) {
		return ((Tree)t).getChild(i);
	}

	public void setChild(Object t, int i, Object child) {
		((Tree)t).setChild(i, (Tree)child);
	}

	public Object deleteChild(Object t, int i) {
		return ((Tree)t).deleteChild(i);
	}

	public int getChildCount(Object t) {
		return ((Tree)t).getChildCount();
	}

	public int getUniqueID(Object node) {
		if ( treeToUniqueIDMap==null ) {
			 treeToUniqueIDMap = new HashMap();
		}
		Integer prevID = (Integer)treeToUniqueIDMap.get(node);
		if ( prevID!=null ) {
			return prevID.intValue();
		}
		int ID = uniqueNodeID;
		treeToUniqueIDMap.put(node, new Integer(ID));
		uniqueNodeID++;
		return ID;
		// GC makes these nonunique:
		// return System.identityHashCode(node);
	}

	/** Tell me how to create a token for use with imaginary token nodes.
	 *  For example, there is probably no input symbol associated with imaginary
	 *  token DECL, but you need to create it as a payload or whatever for
	 *  the DECL node as in ^(DECL type ID).
	 *
	 *  If you care what the token payload objects' type is, you should
	 *  override this method and any other createToken variant.
	 */
	public abstract Token createToken(int tokenType, String text);

	/** Tell me how to create a token for use with imaginary token nodes.
	 *  For example, there is probably no input symbol associated with imaginary
	 *  token DECL, but you need to create it as a payload or whatever for
	 *  the DECL node as in ^(DECL type ID).
	 *
	 *  This is a variant of createToken where the new token is derived from
	 *  an actual real input token.  Typically this is for converting '{'
	 *  tokens to BLOCK etc...  You'll see
	 *
	 *    r : lc='{' ID+ '}' -> ^(BLOCK[$lc] ID+) ;
	 *
	 *  If you care what the token payload objects' type is, you should
	 *  override this method and any other createToken variant.
	 */
	public abstract Token createToken(Token fromToken);
}

