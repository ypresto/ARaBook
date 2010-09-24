package jp.ac.doshisha.drm.divsys.ara.nodes.test;

import javax.media.j3d.Node;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.vecmath.Vector3d;

import com.sun.j3d.utils.geometry.ColorCube;

import jp.ac.doshisha.drm.divsys.ara.ARaNode;
import jp.nyatla.nyartoolkit.NyARException;

/**
 * このノードは40mmの色つき立方体を表示するシーン。ｚ軸を基準に20mm上に浮かせてる。
 */
public class TestNode extends ARaNode {

	public TestNode() throws NyARException {
		super();
	}

	@Override
	protected String getMarkerPatternFilename() {
		return "Data/patt.hiro";
	}

	@Override
	protected double getMarkerWidth() {
		return 0.08;
	}

	/**
	 * シーングラフを作って、そのノードを返す。
	 * このノードは40mmの色つき立方体を表示するシーン。ｚ軸を基準に20mm上に浮かせてる。
	 * NyARJava3Dのデフォルトである。
	 * @return
	 */
	@Override
	public Node createSceneGraph() {
		// Singleton
		TransformGroup tg = new TransformGroup();
		Transform3D mt = new Transform3D();
		mt.setTranslation(new Vector3d(0.00, 0.0, 20 * 0.001));
		// 大きさ 40mmの色付き立方体を、Z軸上で20mm動かして配置）
		tg.setTransform(mt);
		tg.addChild(new ColorCube(20 * 0.001));
		return tg;
	}
}
