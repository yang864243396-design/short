import request from './request'

export const login = (data: { username: string; password: string }) =>
  request.post('/login', data)

export const getDashboard = () => request.get('/dashboard')

export const getDramas = (params: any) => request.get('/dramas', { params })
export const createDrama = (data: any) => request.post('/dramas', data)
export const updateDrama = (id: number, data: any) => request.put(`/dramas/${id}`, data)
export const deleteDrama = (id: number) => request.delete(`/dramas/${id}`)

export const getEpisodes = (params: any) => request.get('/episodes', { params })
export const createEpisode = (data: any) => request.post('/episodes', data)
export const updateEpisode = (id: number, data: any) => request.put(`/episodes/${id}`, data)
export const deleteEpisode = (id: number) => request.delete(`/episodes/${id}`)

export const getUsers = (params: any) => request.get('/users', { params })
export const getUser = (id: number) => request.get(`/users/${id}`)
export const updateUser = (id: number, data: any) => request.put(`/users/${id}`, data)
export const deleteUser = (id: number) => request.delete(`/users/${id}`)

export const adminRechargeUser = (id: number, data: { coins?: number; yuan?: number; remark?: string }) =>
  request.post(`/users/${id}/wallet/recharge`, data)

export const adminDeductUser = (id: number, data: { coins: number; remark?: string }) =>
  request.post(`/users/${id}/wallet/deduct`, data)

export const getAdminWalletTransactions = (params: any) =>
  request.get('/wallet/transactions', { params })

/** 充值订单（含商户单号、平台单号、状态、用户等筛选） */
export const getAdminRechargeOrders = (params: any) =>
  request.get('/recharge-orders', { params })

export const getUserRecentWalletTx = (userId: number, params?: { page?: number; page_size?: number }) =>
  request.get(`/users/${userId}/wallet/recent`, { params })

export const getRechargePackagesAdmin = () => request.get('/recharge-packages')
export const createRechargePackage = (data: any) => request.post('/recharge-packages', data)
export const updateRechargePackage = (id: number, data: any) => request.put(`/recharge-packages/${id}`, data)
export const deleteRechargePackage = (id: number) => request.delete(`/recharge-packages/${id}`)

export const getPayProductConfigsAdmin = () => request.get('/pay-product-configs')
export const createPayProductConfig = (data: any) => request.post('/pay-product-configs', data)
export const updatePayProductConfig = (id: number, data: any) => request.put(`/pay-product-configs/${id}`, data)
export const deletePayProductConfig = (id: number) => request.delete(`/pay-product-configs/${id}`)

export const getAdSkipConfigsAdmin = () => request.get('/ad-skip-configs')
export const createAdSkipConfig = (data: any) => request.post('/ad-skip-configs', data)
export const updateAdSkipConfig = (id: number, data: any) => request.put(`/ad-skip-configs/${id}`, data)
export const deleteAdSkipConfig = (id: number) => request.delete(`/ad-skip-configs/${id}`)

export const getAdmins = (params: any) => request.get('/admins', { params })
export const createAdmin = (data: any) => request.post('/admins', data)
export const updateAdmin = (id: number, data: any) => request.put(`/admins/${id}`, data)
export const deleteAdmin = (id: number) => request.delete(`/admins/${id}`)

export const getRoles = (params?: any) => request.get('/roles', { params })
export const createRole = (data: any) => request.post('/roles', data)
export const updateRole = (id: number, data: any) => request.put(`/roles/${id}`, data)
export const deleteRole = (id: number) => request.delete(`/roles/${id}`)

export const getComments = (params: any) => request.get('/comments', { params })
export const deleteComment = (id: number) => request.delete(`/comments/${id}`)

export const getCategories = (params?: any) => request.get('/categories', { params })
export const createCategory = (data: any) => request.post('/categories', data)
export const updateCategory = (id: number, data: any) => request.put(`/categories/${id}`, data)
export const deleteCategory = (id: number) => request.delete(`/categories/${id}`)

export const getBanners = (params?: any) => request.get('/banners', { params })
export const createBanner = (data: any) => request.post('/banners', data)
export const updateBanner = (id: number, data: any) => request.put(`/banners/${id}`, data)
export const deleteBanner = (id: number) => request.delete(`/banners/${id}`)

export const getAds = (params?: any) => request.get('/ads', { params })
export const createAd = (data: any) => request.post('/ads', data)
export const updateAd = (id: number, data: any) => request.put(`/ads/${id}`, data)
export const deleteAd = (id: number) => request.delete(`/ads/${id}`)

export const uploadVideo = (formData: FormData) =>
  request.post('/upload/video', formData, { headers: { 'Content-Type': 'multipart/form-data' } })

export const uploadImage = (formData: FormData) =>
  request.post('/upload/image', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
