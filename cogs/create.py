import logging

import discord
from discord.ext import commands
from discord_slash import SlashContext, cog_ext
from discord_slash.utils.manage_commands import create_option

guilds = [789150985629073438]


class Create(commands.Cog):
    def __init__(self, bot: commands.Bot):
        self.bot = bot
        self.logger = logging.getLogger('創建系統')

    @cog_ext.cog_slash(name='create', description='為新的一局狼人殺建立一個新的伺服器', options=[
        create_option(name='player_count', description='玩家人數', option_type=int, required=True)],
                       guild_ids=guilds)
    @commands.is_owner()
    async def create(self, ctx: SlashContext, player_count: int):
        # 建立伺服器
        await ctx.defer()
        guild = await self.bot.create_guild('狼人殺暫時群', discord.VoiceRegion.hongkong)
        guild: discord.Guild
        invisible_overwrite = {
            guild.default_role: discord.PermissionOverwrite(
                read_messages=False
            )
        }
        rules = await guild.create_text_channel(name='忽略本頻道', overwrites=invisible_overwrite)
        public_updates = await guild.create_text_channel(name='忽略本頻道', overwrites=invisible_overwrite)
        # 預設只在提及時通知、轉成社群
        try:
            await guild.edit(default_notifications=discord.NotificationLevel.only_mentions, rules_channel=rules,
                             public_updates_channel=public_updates, community=True)
        except discord.Forbidden:
            await guild.edit(default_notifications=discord.NotificationLevel.only_mentions)
            await ctx.send('Discord方尚未完成修復，伺服器創建完成後須自行建立舞台頻道')
        self.logger.info(f'已建立伺服器')
        # everyone 權限
        everyone = guild.default_role
        everyone: discord.Role
        await everyone.edit(permissions=discord.Permissions(permissions=37080128))
        # 法官 身分組
        judge = await guild.create_role(name=f'法官', colour=discord.Colour.blue(), hoist=True,
                                        permissions=discord.Permissions(8))
        self.logger.info(f'已新增法官身分組')
        # 玩家 身分組
        for player in range(player_count):
            player += 1
            await guild.create_role(name=f'玩家 {str(player)}', colour=discord.Colour.teal(),
                                    hoist=True, mentionable=False)
            self.logger.info(f'已新增玩家身分組No.{str(player)}')
        # 發言者 死人(旁觀) 和 非發言者設定
        speaker = await guild.create_role(name=f'發言者', colour=discord.Colour.green(), hoist=True)
        self.logger.info(f'已新增發言者身分組')
        non_speaker = await guild.create_role(name=f'非發言者', colour=discord.Colour.red(), hoist=False)
        self.logger.info(f'已新增非發言者身分組')
        spectator = await guild.create_role(name='旁觀者 / 死人', colour=discord.Colour.dark_red(), hoist=False)
        self.logger.info(f'已新增旁觀者身分組')
        # 3大區設定
        text_category = await guild.create_category(name='文字區', position=0)
        self.logger.info(f'已新增文字分區')
        voice_category = await guild.create_category(name='語音區', position=1)
        self.logger.info(f'已新增語音分區')
        disabled_category = await guild.create_category(name='停用區', position=2)
        self.logger.info(f'已新增停用分區')
        # 公頻
        text_overwrites = {
            speaker: discord.PermissionOverwrite(
                read_messages=True,
                send_messages=True,
            ),
            non_speaker: discord.PermissionOverwrite(
                read_messages=True,
                send_messages=False,
            ),
            spectator: discord.PermissionOverwrite(
                read_messages=True,
                send_messages=False,
            )
        }
        voice_overwrites = {
            speaker: discord.PermissionOverwrite(
                connect=True,
                speak=True,
            ),
            non_speaker: discord.PermissionOverwrite(
                connect=True,
                speak=False,
            ),
            spectator: discord.PermissionOverwrite(
                connect=True,
                speak=False,
            )
        }
        public_text = await guild.create_text_channel(name='法院', category=text_category, overwrites=text_overwrites)
        self.logger.info(f'已新增法院文頻')
        try:
            public_voice = await guild.create_stage_channel(name='法院', topic='為你被質疑的事和所作所為進行辯解',
                                                            category=voice_category,
                                                            overwrites=voice_overwrites)
        except:
            pass
            #public_voice = await guild.create_voice_channel(name='法院',
            #                                                category=voice_category,
            #                                                overwrites=voice_overwrites)
        self.logger.info(f'已新增法院音頻')
        temp_voice_deny_all_overwrites = {
            guild.default_role: discord.PermissionOverwrite(
                view_channel=False
            )
        }
        self.logger.info(f'已新增暫時音頻')
        await guild.create_voice_channel(name='暫時語音', category=disabled_category,
                                         overwrites=temp_voice_deny_all_overwrites)
        # 旁觀頻
        dead_text_overwrite = {
            spectator: discord.PermissionOverwrite(
                read_messages=True
            ),
            guild.default_role: discord.PermissionOverwrite(
                read_messages=False
            )
        }
        dead_voice_overwrite = {
            spectator: discord.PermissionOverwrite(
                view_channel=True
            ),
            guild.default_role: discord.PermissionOverwrite(
                view_channel=False
            )
        }
        public_text = await guild.create_text_channel(name='場外', category=text_category, overwrites=dead_text_overwrite)
        self.logger.info(f'已新增場外文頻')
        public_typing = await guild.create_text_channel(name='打字區', category=text_category, overwrites={
            guild.default_role: discord.PermissionOverwrite(read_message_history=False)})
        dead_voice = await guild.create_voice_channel(name='場外', category=voice_category,
                                                      overwrites=dead_voice_overwrite)
        self.logger.info(f'已新增場外音頻')
        # 身分創頻
        to_be_created_channels = ['狼人', '狼美人', '狼兄弟', '女巫', '預言家', '獵人', '騎士', '守衛', '黑市商人', '平民', '平民', '平民', '平民']
        # 先拒絕所有人讀取的權限，但接受旁觀
        text_deny_overwrite = {
            spectator: discord.PermissionOverwrite(
                read_messages=True,
                send_messages=False
            ),
            guild.default_role: discord.PermissionOverwrite(
                read_messages=False
            )
        }
        voice_deny_overwrite = {
            spectator: discord.PermissionOverwrite(
                view_channel=True,
                connect=True,
                speak=False
            ),
            guild.default_role: discord.PermissionOverwrite(
                view_channel=False
            )
        }
        for tbc_channel in to_be_created_channels:
            if tbc_channel == '狼人':
                await guild.create_text_channel(name='狼人文字頻道', category=text_category, overwrites=text_deny_overwrite)
                self.logger.info(f'已新增狼人文頻')
            await guild.create_voice_channel(name=tbc_channel, category=voice_category, overwrites=voice_deny_overwrite)
            self.logger.info(f'已新增{tbc_channel}音頻')
        self.logger.info(f'創建完成')
        await ctx.send(
            '你需要使用此連結來允許機器人在暫時群裡接受斜線指令：https://discord.com/api/oauth2/authorize?client_id=804332838559809616&scope=applications.commands')
        await ctx.send(f'完成，邀請連結：{str(await public_text.create_invite())}')

    @cog_ext.cog_slash(name='judge', description='讓他升官吧！', options=[
        create_option(name='member', description='要使成為法官的人', option_type=6, required=True)])
    @commands.is_owner()
    async def judge(self, ctx, member: discord.Member):
        await member.add_roles(discord.utils.get(ctx.guild.roles, name='法官'))
        await ctx.send('完成')

    @commands.command(name='judge', brief='讓他升官吧！', usage='<要使成為法官的人>')
    @commands.is_owner()
    async def _judge(self, ctx, member: discord.Member):
        await member.add_roles(discord.utils.get(ctx.guild.roles, name='法官'))
        await ctx.send('完成')

    @cog_ext.cog_slash(name='demote', description='讓他被貶為庶民吧！', options=[
        create_option(name='member', description='要使取消法官的人', option_type=6, required=True)])
    @commands.is_owner()
    async def demote(self, ctx, member: discord.Member):
        await member.remove_roles(discord.utils.get(ctx.guild.roles, name='法官'))
        await ctx.send('完成')

    @cog_ext.cog_slash(name='destroy', description='刪除暫時群伺服器')
    @commands.is_owner()
    async def destroy(self, ctx):
        self.logger.warning(f'{str(ctx.message.author.id)} 已刪除 {str(ctx.message.guild.id)}')
        await ctx.guild.delete()

    @cog_ext.cog_slash(name='leave', description='自非主群離開', guild_ids=guilds)
    @commands.is_owner()
    async def leave(self, ctx):
        await ctx.defer()
        for guild in self.bot.guilds:
            if guild.name == '狼人殺暫時群':
                try:
                    await guild.leave()
                except:
                    await guild.delete()
                logging.error(f'已離開 {guild.id}')
        await ctx.send('完成')

    @cog_ext.cog_slash(name='create_court', description='建立法院舞台頻道')
    @commands.is_owner()
    async def create_court(self, ctx):
        voice_category = discord.utils.get(ctx.guild.categories, name='語音區')
        public_voice = await ctx.guild.create_stage_channel(name='法院', topic='為你被質疑的事和所作所為進行辯解',
                                                            category=voice_category)
        await ctx.send('完成')


def setup(bot: commands.Bot):
    bot.add_cog(Create(bot))
