import logging
import random

import discord
from discord.ext import commands
from discord_slash import cog_ext
from discord_slash.context import SlashContext


class Ready(commands.Cog):
    def __init__(self, bot: commands.Bot):
        self.bot = bot
        self.logger = logging.getLogger('準備系統')

    @cog_ext.cog_slash(name='role', description='除了法官身分的人都獲得隨機身分組')
    @commands.has_permissions(administrator=True)
    async def role(self, ctx: SlashContext):
        await ctx.defer()
        judge_role = discord.utils.get(ctx.guild.roles, name='法官')
        role_pool = []
        for role in ctx.guild.roles:
            if str(role).startswith('玩家 '):
                role_pool.append(role)
        for member in ctx.guild.members:
            member: discord.Member
            if member.bot:
                continue
            if judge_role.id in [i.id for i in member.roles]:
                continue
            reward = random.choice(role_pool)
            role_pool.remove(reward)
            await member.add_roles(reward)
            self.logger.info(f'{str(member)} 獲得 {str(reward)}')
            await member.edit(nick=reward.name)
        await ctx.send('完成')

    @cog_ext.cog_slash(name='sprite', description='將所有身分組隨機分配至各職業語音(將頻道搬到停用類別即可停用該職業，各職業之數量將為其語音頻道最大人數限制)')
    @commands.has_permissions(administrator=True)
    async def sprite(self, ctx: SlashContext):
        await ctx.defer()
        excluded = ['法院', '場外']
        roles = []
        for role in ctx.guild.roles:
            if '玩家' in str(role):
                roles.append(role)
        pool = []
        active_cat = discord.utils.get(ctx.guild.categories, name='語音區')
        active_cat: discord.CategoryChannel
        for channel in active_cat.channels:
            channel: discord.VoiceChannel
            if channel.name in excluded:
                continue
            if '狼人' in channel.name:
                self.logger.info(f'共有{channel.user_limit}個狼')
                for i in range(channel.user_limit):
                    pool.append(channel)
                continue
            pool.append(channel)
        if len(pool) != len(roles):
            await ctx.send('出事了, 玩家人數不等於有效職業語音個數(你有設定狼人語音最大人數嗎)')
            self.logger.error(f'玩家身分組，共{len(roles)}個，如下：{"，".join(x.name for x in roles)}')
            self.logger.error(f'語音頻道，共{len(pool)}個，如下：{"，".join(x.name for x in pool)}')
            return
        for role in roles:
            reward = random.choice(pool)
            reward: discord.VoiceChannel
            await reward.set_permissions(target=role,
                                         overwrite=discord.PermissionOverwrite(view_channel=True, connect=True,
                                                                               speak=True))
            if '狼人' in reward.name:
                # 若為狼人則追加文字頻道
                tc = discord.utils.get(ctx.guild.channels, name='狼人文字頻道')
                tc: discord.TextChannel
                await tc.set_permissions(target=role,
                                         overwrite=discord.PermissionOverwrite(read_messages=True, send_messages=True))
            pool.remove(reward)
        await ctx.send('完成')


def setup(bot: commands.Bot):
    bot.add_cog(Ready(bot))
