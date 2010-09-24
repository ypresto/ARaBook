package jp.ac.doshisha.drm.divsys.ara;

import javax.media.j3d.Group;
import javax.media.j3d.Node;
import javax.media.j3d.Switch;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;

import jp.nyatla.nyartoolkit.NyARException;
import jp.nyatla.nyartoolkit.core.NyARCode;
import jp.nyatla.nyartoolkit.core.param.NyARParam;
import jp.nyatla.nyartoolkit.java3d.utils.NyARSingleMarkerBehaviorListener;

public abstract class ARaNode implements NyARSingleMarkerBehaviorListener {

	/**
	 * シーングラフを作成する関数。
	 * 各ノードごとに定義する。
	 */
	protected abstract Node createSceneGraph();
	
	/**
	 * マーカーパターンのファイル名を指定するための抽象メソッド。
	 * @return 読み込むマーカーのファイル名。
	 */
	protected abstract String getMarkerPatternFilename();
	
	/**
	 * マーカーパターンのサイズをメートル単位で指定するための抽象メソッド。。
	 * @return メートル単位のマーカーのサイズ。
	 */
	protected abstract double getMarkerWidth();

	/**
	 * マーカーパターンの縦横のピクセルサイズを指定するためのメソッド。
	 * 必要に応じてオーバーライドすること。
	 * @return マーカーのピクセル単位のサイズ
	 */
	protected int getMarkerSizePixel() {
		return 16;
	}

	private NyARCode ar_code = null;
	private double marker_width = -1;
	private boolean auto_hide = true;
	private boolean is_hidden = false;

	private Switch scene_graph_switch;

	private boolean is_marker_exist;
	private Transform3D transform3d;

	public ARaNode() throws NyARException {
		this.initMarker();
		this.scene_graph_switch = new Switch(Switch.CHILD_NONE);
		this.scene_graph_switch.setCapability(Switch.ALLOW_SWITCH_WRITE);
		this.scene_graph_switch.addChild(createSceneGraph());
	}

	public Node getSceneGraph() {
		return this.scene_graph_switch;
	}

	/**
	 * マーカーとその大きさを設定
	 */
	public void setMarker(NyARCode i_ar_code, double i_marker_width) {
		this.ar_code = i_ar_code;
		this.marker_width = i_marker_width;
	}
	
	public void setAutoHide(boolean i_auto_hide) {
		this.auto_hide = i_auto_hide;
	}
	
	/**
	 * マーカーが画面内に存在するか否かを取得。
	 * NyARMarkerBehaviorHolderを利用している場合は、
	 * renewNodesByUpdate()を呼び出す必要がある。
	 */
	public boolean isExist() {
		return this.is_marker_exist;
	}

	/**
	 * hide()で明示的に非表示にされているかを取得。
	 */
	public boolean isHidden() {
		return this.is_hidden;
	}
	
	public void hide() {
		this.is_hidden = true;
		_hide();
	}
	
	public void show() {
		this.is_hidden = false;
		if (!this.auto_hide && this.is_marker_exist) {
			_show();
		}
	}

	/**
	 * NyARSingleMarkerBehaviorListenerの基本的な実装。
	 * フィールドを更新する。
	 */
	public void onUpdate(boolean i_is_marker_exist, Transform3D i_transform3d) {
		this.is_marker_exist = i_is_marker_exist;
		this.transform3d = i_transform3d;
		this._renew_hidden();
	}

	/**
	 * NyARMarkerBehaviorListenerの実装における
	 * onUpdate()に渡される引数（配列）を、ARaNode型配列の各要素にマッピングする。
	 * 一種のO/R Mapperみたいなもの？
	 * NyARMarkerBehaviorHolderを利用する場合は、一部操作に必須。
	 */
	public static void renewNodesByUpdate(ARaNode[] i_nodes, boolean[] i_is_marker_exist, Transform3D[] i_transform3d) {
		for (int i = 0; i < i_nodes.length; i++) {
			i_nodes[i].is_marker_exist = i_is_marker_exist[i];
			i_nodes[i].transform3d = i_transform3d[i];
			i_nodes[i]._renew_hidden();
		}
	}

	/**
	 * ARaNode型配列をもとに、NyARMarkerBehaviorHolderを作成する。
	 * transformGroupの作成および登録、setTransformGroup()を自動で行う。
	 * @param i_nodes ノードの配列
	 * @param i_gr 作成されるTransformGroupを所属させたいGroup
	 * @return 作成されたNyARMarkerBehaviorHolder
	 * @throws NyARException
	 */
	public static NyARMarkerBehaviorHolder createNyARMarkerBehaviorHolder(NyARParam i_cparam, float i_rate, ARaNode[] i_nodes, double i_min_confidence, Group i_gr) throws NyARException {
		NyARMarkerBehaviorHolder holder;
		NyARCode[] ar_code_array = new NyARCode[i_nodes.length];
		double[] marker_width_array = new double[i_nodes.length];
		// ar_codeとmarker_widthを格納しているARaNodeクラス群から、配列を作成する。
		for (int i = 0; i < i_nodes.length; i++) {
			ar_code_array[i] = i_nodes[i].ar_code;
			marker_width_array[i] = i_nodes[i].marker_width;
		}
		holder = new NyARMarkerBehaviorHolder(i_cparam, i_rate, ar_code_array, marker_width_array, i_nodes.length, i_min_confidence);
		// 各要素のTransformGroupを生成、登録する。ただし、getSceneGraph()がnullの場合は飛ばす。
		for (int i = 0; i < i_nodes.length; i++) {
			Node sg = i_nodes[i].getSceneGraph();
			if (sg != null) {
				TransformGroup tg = new TransformGroup();
				tg.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
				tg.addChild(sg);
				i_gr.addChild(tg);
				holder.setTransformGroup(i, tg);
			}
		}
		return holder; 
	}
	
	private void initMarker() throws NyARException {
		this.ar_code = new NyARCode(this.getMarkerSizePixel(), this.getMarkerSizePixel());
		this.ar_code.loadARPattFromFile(this.getMarkerPatternFilename());
		this.marker_width = getMarkerWidth();
	}

	/**
	 * is_marker_existやhiddenが更新されたら呼び出すこと。
	 * ほぼauto_hideのためのハンドラ。
	 */
	private void _renew_hidden() {
		if (!this.auto_hide || this.is_hidden) {
			return;
		}
		else if (this.is_marker_exist) {
			_show();
		}
		else {
			_hide();
		}
	}

	/**
	 * auto_hideとかhiddenとか関係なしに、とりあえず非表示にする。
	 */
	private void _hide() {
		this.scene_graph_switch.setWhichChild(Switch.CHILD_NONE);
	}

	/**
	 * auto_hideとかhiddenとか関係なしに、とりあえず非表示を解除する。
	 */
	private void _show() {
		this.scene_graph_switch.setWhichChild(Switch.CHILD_ALL);
	}

}
