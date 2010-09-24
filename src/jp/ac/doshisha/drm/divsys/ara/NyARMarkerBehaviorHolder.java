/* 
 * PROJECT: NyARToolkit Java3D utilities.
 * --------------------------------------------------------------------------------
 * The MIT License
 * Copyright (c) 2008 nyatla
 * airmail(at)ebony.plala.or.jp
 * http://nyatla.jp/nyartoolkit/
 * 
 * NyARMarkerBehaviorHolder
 * Copyright (c) 2010 yuya_presto
 * Forked from NyARSingleMarkerBehaviorHolder
 * to add support for many markers
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * 
 */
package jp.ac.doshisha.drm.divsys.ara;

import java.util.Enumeration;

import javax.media.Buffer;
import javax.media.j3d.*;
import javax.vecmath.*;

import jp.nyatla.nyartoolkit.NyARException;
import jp.nyatla.nyartoolkit.java3d.utils.*;
import jp.nyatla.nyartoolkit.jmf.utils.*;
import jp.nyatla.nyartoolkit.core.*;
import jp.nyatla.nyartoolkit.core.param.NyARParam;
import jp.nyatla.nyartoolkit.core.transmat.NyARTransMatResult;
import jp.nyatla.nyartoolkit.detector.*;
import jp.nyatla.nyartoolkit.core.types.*;

/**
 * NyARToolkitと連動したBehaviorを返却するクラスです。
 * 提供できるBehaviorは、BackgroundとTransformgroupです。
 *
 */
public class NyARMarkerBehaviorHolder implements JmfCaptureListener
{
	private NyARParam _cparam;

	private JmfCaptureDevice _capture;

	private J3dNyARRaster_RGB _nya_raster;//最大3スレッドで共有されるので、排他制御かけること。

	private NyARDetectMarker _nya;

	//Behaviorホルダ
	private NyARBehaviorMany _nya_behavior;
	
	private double min_confidence;

	public NyARMarkerBehaviorHolder(NyARParam i_cparam, float i_rate, NyARCode[] i_ar_code, double[] i_marker_width, int i_number_of_code, double i_min_confidence) throws NyARException
	{
		this.min_confidence = i_min_confidence;
		final NyARIntSize scr_size = i_cparam.getScreenSize();
		this._cparam = i_cparam;
		//キャプチャの準備
		JmfCaptureDeviceList devlist=new JmfCaptureDeviceList();
		this._capture=devlist.getDevice(0);
		this._capture.setCaptureFormat(scr_size.w, scr_size.h,15f);
		this._capture.setOnCapture(this);		
		this._nya_raster = new J3dNyARRaster_RGB(this._cparam,this._capture.getCaptureFormat());
		this._nya = new NyARDetectMarker(this._cparam, i_ar_code, i_marker_width, i_number_of_code,this._nya_raster.getBufferType());
		this._nya_behavior = new NyARBehaviorMany(this._nya, i_number_of_code, this.min_confidence, this._nya_raster, i_rate);
	}

	public Behavior getBehavior()
	{
		return this._nya_behavior;
	}

	/**
	 * i_back_groundにキャプチャ画像を転送するようにBehaviorを設定します。
	 * i_back_groungはALLOW_IMAGE_WRITE属性を持つものである必要があります。
	 * @param i_back_groung
	 * @return
	 */
	public void setBackGround(Background i_back_ground)
	{
		//コール先で排他制御
		this._nya_behavior.setRelatedBackGround(i_back_ground);
	}

	/**
	 * i_trgroupの座標系をマーカーにあわせるようにBehaviorを設定します。
	 * @param i_index
	 * マーカーのインデックス番号を指定します。
	 */
	public void setTransformGroup(int i_index, TransformGroup i_trgroup)
	{
		//コール先で排他制御
		this._nya_behavior.setRelatedTransformGroup(i_index, i_trgroup);
	}

	/**
	 * 座標系再計算後に呼び出されるリスナです。
	 * @param i_index
	 * マーカーのインデックス番号を指定します。
	 * @param i_listener
	 */
	public void setUpdateListener(int i_index, NyARMarkerBehaviorListener i_listener)
	{
		//コール先で排他制御
		this._nya_behavior.setUpdateListener(i_listener);
	}

	/**
	 * ラスタを更新 コールバック関数だから呼んじゃらめえ
	 */
	public void onUpdateBuffer(Buffer i_buffer)
	{
		try {
			synchronized (this._nya_raster) {
				this._nya_raster.setBuffer(i_buffer);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void start() throws NyARException
	{
		//開始
		this._capture.start();
	}

	public void stop()
	{
		this._capture.stop();
	}
}

class NyARBehaviorMany extends Behavior
{
	private NyARTransMatResult trans_mat_result = new NyARTransMatResult();

	private NyARDetectMarker related_nya;

	private TransformGroup[] trgroups;

	private Background back_ground;

	private J3dNyARRaster_RGB raster;

	private WakeupCondition wakeup;

	private NyARMarkerBehaviorListener listener;
	
	private int number_of_code;

	private double min_confidence;

	public void initialize()
	{
		wakeupOn(wakeup);
	}

	/**
	 * i_related_ic2dの内容で定期的にi_back_groundを更新するBehavior
	 * @param i_back_ground
	 * @param i_related_ic2d
	 */
	public NyARBehaviorMany(NyARDetectMarker i_related_nya, int i_number_of_code, double i_min_confidence, J3dNyARRaster_RGB i_related_raster, float i_rate)
	{
		super();
		number_of_code = i_number_of_code;
		wakeup = new WakeupOnElapsedTime((int) (1000 / i_rate));
		related_nya = i_related_nya;
		trgroups = new TransformGroup[i_number_of_code];
		raster = i_related_raster;
		back_ground = null;
		listener = null;
		this.setSchedulingBounds(new BoundingSphere(new Point3d(), 100.0));
		min_confidence = i_min_confidence;
	}

	public void setRelatedBackGround(Background i_back_ground)
	{
		synchronized (raster) {
			back_ground = i_back_ground;
		}
	}

	public void setRelatedTransformGroup(int i_index, TransformGroup i_trgroup)
	{
		synchronized (raster) {
			trgroups[i_index] = i_trgroup;
		}
	}

	public void setUpdateListener(NyARMarkerBehaviorListener i_listener)
	{
		synchronized (raster) {
			listener = i_listener;
		}
	}

	/**
	 * いわゆるイベントハンドラ
	 */
	public void processStimulus(Enumeration criteria)
	{
		try {
			synchronized (raster) {
				Transform3D[] t3d = new Transform3D[number_of_code];
				boolean[] is_marker_exist = new boolean[number_of_code];
				int num_marker_exist = 0;
				if (back_ground != null) {
					raster.renewImageComponent2D();/*DirectXモードのときの対策*/
					back_ground.setImage(raster.getImageComponent2D());
				}
				if (raster.hasBuffer()) {
					num_marker_exist = related_nya.detectMarkerLite(raster, 100);
					final NyARTransMatResult src = this.trans_mat_result;
					for (int i = 0; i < num_marker_exist; i++)
					{
						if (related_nya.getConfidence(i) < min_confidence) {
							continue;
						}
						int arcode_index = related_nya.getARCodeIndex(i);
						is_marker_exist[arcode_index] = true;
						if (trgroups[arcode_index] != null) {
							related_nya.getTransmationMatrix(i, src);
//							Matrix4d matrix = new Matrix4d(src.m00, -src.m10, -src.m20, 0, -src.m01, src.m11, src.m21, 0, -src.m02, src.m12, src.m22, 0, -src.m03, src.m13, src.m23, 1);
							Matrix4d matrix = new Matrix4d(
									-src.m00, -src.m10, src.m20, 0,
									-src.m01, -src.m11, src.m21, 0,
									-src.m02, -src.m12, src.m22, 0,
									-src.m03,-src.m13, src.m23, 1);
							matrix.transpose();
							t3d[arcode_index] = new Transform3D(matrix);
							trgroups[arcode_index].setTransform(t3d[arcode_index]);
						}
					}
				}
				listener.onUpdate(is_marker_exist, t3d);
			}
			wakeupOn(wakeup);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
