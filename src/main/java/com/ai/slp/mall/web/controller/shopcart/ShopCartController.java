package com.ai.slp.mall.web.controller.shopcart;

import com.ai.opt.base.exception.BusinessException;
import com.ai.opt.base.exception.SystemException;
import com.ai.opt.sdk.components.ccs.CCSClientFactory;
import com.ai.opt.sdk.components.idps.IDPSClientFactory;
import com.ai.opt.sdk.dubbo.util.DubboConsumerFactory;
import com.ai.opt.sdk.web.model.ResponseData;
import com.ai.opt.sso.client.filter.SLPClientUser;
import com.ai.opt.sso.client.filter.SSOClientConstants;
import com.ai.paas.ipaas.ccs.constants.ConfigException;
import com.ai.paas.ipaas.image.IImageClient;
import com.ai.paas.ipaas.util.JSonUtil;
import com.ai.slp.mall.web.constants.SLPMallConstants;
import com.ai.slp.order.api.ordertradecenter.interfaces.IOrderTradeCenterSV;
import com.ai.slp.order.api.ordertradecenter.param.OrdBaseInfo;
import com.ai.slp.order.api.ordertradecenter.param.OrdProductInfo;
import com.ai.slp.order.api.ordertradecenter.param.OrderTradeCenterRequest;
import com.ai.slp.order.api.ordertradecenter.param.OrderTradeCenterResponse;
import com.ai.slp.order.api.shopcart.interfaces.IShopCartSV;
import com.ai.slp.order.api.shopcart.param.*;
import com.alibaba.fastjson.JSON;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by liutong5 on 16/5/30.
 */
@Controller
@RequestMapping("/shopcart")
public class ShopCartController {
    private static final Logger LOG = LoggerFactory.getLogger(ShopCartController.class);
    /**
     * 购物车中添加商品
     */
    @RequestMapping("/addProd")
    @ResponseBody
    public ResponseData<CartProdOptRes> addProd(HttpSession session, @RequestParam Long buyNum, @RequestParam String skuId) {
        //获取service
        IShopCartSV iShopCartSV = DubboConsumerFactory.getService("IShopCartSV");
        ResponseData<CartProdOptRes> responseData = null;
        try{
            int skuNumLimit = getSkuNumLimit();
            if (skuNumLimit>0 && buyNum>skuNumLimit){
                throw new BusinessException("","此商品添加数量超过限制,不允许添加");
            }
            //设置参数
            CartProd cartProd = new CartProd();
            cartProd.setBuyNum(buyNum);
            cartProd.setSkuId(skuId);
            cartProd.setTenantId(SLPMallConstants.COM_TENANT_ID);
            cartProd.setUserId(getUserId(session));
            CartProdOptRes cartProdOptRes = iShopCartSV.addProd(cartProd);
            LOG.debug("添加购物车商品出参:"+ JSonUtil.toJSon(cartProdOptRes));
            responseData = new ResponseData<CartProdOptRes>(ResponseData.AJAX_STATUS_SUCCESS, "添加成功", cartProdOptRes);
        }catch(BusinessException|SystemException e){
            responseData = new ResponseData<CartProdOptRes>(ResponseData.AJAX_STATUS_FAILURE, "添加失败:"+e.getMessage());
            LOG.error("添加购物车商品出错",e);
        }catch (Exception e){
            responseData = new ResponseData<CartProdOptRes>(ResponseData.AJAX_STATUS_FAILURE, "添加失败,出现未知异常");
            LOG.error("添加购物车商品出错",e);
        }
        return responseData;
    }
    /**
     * 查询用户的购物车详细信息
     */
    @RequestMapping("/cartDetails")
    @ResponseBody
    public ModelAndView queryCartDetails(HttpSession session){
    	Map<String, Object> model = new HashMap<String, Object>();
    	try{
    		IShopCartSV iShopCartSV = DubboConsumerFactory.getService("IShopCartSV");
    		UserInfo userInfo = new UserInfo();
            userInfo.setTenantId(SLPMallConstants.COM_TENANT_ID);
    		userInfo.setUserId(getUserId(session));
            List<CartProdInfo> cartProdInfoList = iShopCartSV.queryCartOfUser(userInfo);
    		//统计商品数量
    		int prodTotal = 0;
            IImageClient imageClient = IDPSClientFactory.getImageClient(SLPMallConstants.ProductImageConstant.IDPSNS);
    		String attrImageSize = "75x48";
            for(CartProdInfo cartProdInfo : cartProdInfoList){
    			prodTotal+=cartProdInfo.getBuyNum();
                //产生图片地址
                if (StringUtils.isNotBlank(cartProdInfo.getVfsId())){
                    String vfsId = cartProdInfo.getVfsId();
                    String picType = cartProdInfo.getPicType();
                    if (StringUtils.isBlank(picType))
                        picType = ".jpg";
                    if (!picType.startsWith("."))
                        picType = "."+picType;
                    String imageUrl = imageClient.getImageUrl(vfsId, picType, attrImageSize);
                    cartProdInfo.setPicUrl(imageUrl);
                }
    		}
            String cartProdInfoJSON = JSonUtil.toJSon(cartProdInfoList);
            model.put("cartProdList", cartProdInfoJSON);
            model.put("prodTotal", prodTotal);
            model.put("skuNumLimit",getSkuNumLimit());
    	}catch(Exception e){
    		e.printStackTrace();
    		LOG.error("查询购物车商品详情出错",e);
    	}
        return new ModelAndView("jsp/shopcart/shopping_cart",model);
    }
    /**
     * 修改购物车数量
     */
    @RequestMapping("/updateProdNum")
    @ResponseBody
    public ResponseData<CartProdOptRes> updateProdNum(HttpSession session, @RequestParam Long buyNum, @RequestParam String skuId) {
        IShopCartSV iShopCartSV = DubboConsumerFactory.getService("IShopCartSV");
        ResponseData<CartProdOptRes> responseData = null;
        try {
            int skuNumLimit = getSkuNumLimit();
            if (skuNumLimit>0 && buyNum>skuNumLimit){
                throw new BusinessException("","此商品添加数量超过限制,不允许添加");
            }
            //设置参数
            CartProd cartProd = new CartProd();
            cartProd.setBuyNum(buyNum);
            cartProd.setSkuId(skuId);
            cartProd.setTenantId(SLPMallConstants.COM_TENANT_ID);
            cartProd.setUserId(getUserId(session));
            CartProdOptRes cartProdOptRes = iShopCartSV.updateProdNum(cartProd);
            LOG.debug("修改购物车数量出参:" + JSonUtil.toJSon(cartProdOptRes));
            responseData = new ResponseData<CartProdOptRes>(ResponseData.AJAX_STATUS_SUCCESS, "修改成功", cartProdOptRes);
        } catch (BusinessException | SystemException e) {
            responseData = new ResponseData<CartProdOptRes>(ResponseData.AJAX_STATUS_FAILURE, "修改失败:" + e.getMessage());
            LOG.error("修改购物车数量出错", e);
        } catch (Exception e) {
            responseData = new ResponseData<CartProdOptRes>(ResponseData.AJAX_STATUS_FAILURE, "修改失败,出现未知异常");
            LOG.error("修改购物车数量出错", e);
        }
        return responseData;
    }
    
    /**
     * 删除购物车商品
     */
    @RequestMapping("/deleteProd")
    @ResponseBody
    public ResponseData<CartProdOptRes> deleteMultiProd(HttpSession session, String skuList) {
        ResponseData<CartProdOptRes> responseData = null;
        try {
            List<String> skuIds = JSON.parseArray(skuList, String.class);
            CartProdOptRes cartProdOptRes = delProdOfCart(session,skuIds);
            LOG.debug("删除购物车商品出参:" + JSonUtil.toJSon(cartProdOptRes));
            responseData = new ResponseData<CartProdOptRes>(ResponseData.AJAX_STATUS_SUCCESS, "删除成功", cartProdOptRes);
        } catch (BusinessException | SystemException e) {
            responseData = new ResponseData<CartProdOptRes>(ResponseData.AJAX_STATUS_FAILURE, "删除失败:" + e.getMessage());
            LOG.error("删除购物车商品出错", e);
        } catch (Exception e) {
            responseData = new ResponseData<CartProdOptRes>(ResponseData.AJAX_STATUS_FAILURE, "删除失败,出现未知异常");
            LOG.error("删除购物车商品出错", e);
        }
        return responseData;
    }

    /**
     * 购物车中下订单
     * @return
     */
    @RequestMapping("/applyOrder")
    public String applyOrder(HttpSession session, String prodObj, Model uiModel,RedirectAttributes redirectAttributes){
        IOrderTradeCenterSV ordertradeSV = DubboConsumerFactory.getService("iOrderTradeCenterSV");
        String viewStr = "jsp/order/order_submit";
        try {
            OrderTradeCenterRequest orderTradeReq = new OrderTradeCenterRequest();
            orderTradeReq.setTenantId(SLPMallConstants.COM_TENANT_ID);
            OrdBaseInfo ordBaseInfo = new OrdBaseInfo();
            ordBaseInfo.setUserId(getUserId(session));
            ordBaseInfo.setOrderType(SLPMallConstants.SHOP_CART_ORDE_TYPE);
            orderTradeReq.setOrdBaseInfo(ordBaseInfo);
            List<OrdProductInfo> infoList = JSON.parseArray(prodObj, OrdProductInfo.class);
            orderTradeReq.setOrdProductInfoList(infoList);
            OrderTradeCenterResponse response = ordertradeSV.apply(orderTradeReq);
            uiModel.addAttribute("ordProductResList",response.getOrdProductResList());
            uiModel.addAttribute("orderId",response.getOrderId());
            uiModel.addAttribute("ordFeeInfo", response.getOrdFeeInfo());
            uiModel.addAttribute("expFee", 0);
            uiModel.addAttribute("balanceFee", 0);
            uiModel.addAttribute("balance", 0);
            //从购物车中删除商品.
            List<String> skuIds = new ArrayList<>();
            for (OrdProductInfo productInfo:infoList){
                skuIds.add(productInfo.getSkuId());
            }
            delProdOfCart(session,skuIds);
        } catch (BusinessException e) {
            LOG.error("提交订单出错", e);
            redirectAttributes.addFlashAttribute("errMsg","订单提交错误:"+e.getMessage());
            viewStr = "redirect:cartDetails";
        } catch (Exception e) {
            LOG.error("提交订单出错", e);
            redirectAttributes.addFlashAttribute("errMsg","订单提交错误:未知异常");
            viewStr = "redirect:cartDetails";
        }
        return viewStr;
    }

    private String getUserId(HttpSession session){
        SLPClientUser user = (SLPClientUser) session.getAttribute(SSOClientConstants.USER_SESSION_KEY);
//        return "234";
        return user.getUserId();
    }

    /**
     * 获取购物车中单个商品的数量限制
     * @return
     */
    private int getSkuNumLimit (){
        String limitNum = null;
        try {
            limitNum = CCSClientFactory.getDefaultConfigClient().get("/shop_cart_sku_num_limit");
        } catch (ConfigException e) {
            LOG.error("获取配置信息失败",e);
            e.printStackTrace();
        }
        return Integer.parseInt(limitNum);
    }

    /**
     * 删除购物车中商品
     */
    private CartProdOptRes delProdOfCart(HttpSession session,List<String> skuIds){
        IShopCartSV iShopCartSV = DubboConsumerFactory.getService("IShopCartSV");
        //设置参数
        MultiCartProd multiCartProd = new MultiCartProd();
        multiCartProd.setTenantId(SLPMallConstants.COM_TENANT_ID);
        multiCartProd.setUserId(getUserId(session));
        multiCartProd.setSkuIdList(skuIds);
        return iShopCartSV.deleteMultiProd(multiCartProd);
    }
    
}
